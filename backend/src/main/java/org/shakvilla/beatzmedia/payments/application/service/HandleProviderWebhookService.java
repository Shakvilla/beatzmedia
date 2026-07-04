package org.shakvilla.beatzmedia.payments.application.service;

import java.nio.charset.StandardCharsets;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.payments.application.port.in.HandleProviderWebhook;
import org.shakvilla.beatzmedia.payments.application.port.in.WebhookResult;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentEventRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentRepository;
import org.shakvilla.beatzmedia.payments.domain.PaymentEvent;
import org.shakvilla.beatzmedia.payments.domain.PaymentEventType;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.WebhookSignatureException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Application service implementing {@link HandleProviderWebhook} (LLFR-PAYMENTS-01.2).
 *
 * <p>Flow (payments ADD §8a):
 *
 * <ol>
 *   <li>Verify the signature over the <strong>raw</strong> bytes via {@link PaymentGateway}; invalid
 *       → {@link WebhookSignatureException} (401), before any parsing or DB access.
 *   <li>Parse the (now-trusted) payload; a malformed body is a {@code 422} {@code VALIDATION}.
 *   <li>Resolve the intent by {@code providerRef}; unknown → {@link WebhookResult#IGNORED_UNKNOWN}
 *       (202, accept-and-ignore so the provider does not retry-storm).
 *   <li>Record a {@code payment_event} keyed on {@code providerEventId} (UNIQUE); a duplicate →
 *       {@link WebhookResult#DUPLICATE} (200 no-op) — the intent is <em>not</em> re-transitioned and
 *       no second event is emitted.
 *   <li>On first delivery, transition the intent via {@link PaymentSettlementService} (which emits
 *       {@code PaymentSettled}/{@code PaymentFailed} AFTER_SUCCESS) → {@link WebhookResult#HANDLED}.
 * </ol>
 *
 * <p><strong>Atomicity + exactly-once (INV-1).</strong> The whole method is one transaction: event
 * recording, the state transition, and the (AFTER_SUCCESS) event publication commit together or not
 * at all. The {@code provider_event_id} UNIQUE constraint is the durable idempotency backstop, so a
 * duplicate webhook applies the settlement at most once and emits exactly one settlement event.
 *
 * <p>No {@code AuditEntry} is written here: settlement is a system/provider-driven transition, not a
 * privileged human money mutation (the audited set is run-weekly/send/refund/reject/escalate — ADD
 * §9); the {@code payment_event} row is the durable settlement audit trail.
 */
@ApplicationScoped
public class HandleProviderWebhookService implements HandleProviderWebhook {

  private final PaymentGateway gateway;
  private final PaymentRepository intents;
  private final PaymentEventRepository events;
  private final PaymentSettlementService settlement;
  private final HandleChargebackService chargebacks;
  private final ObjectMapper objectMapper;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public HandleProviderWebhookService(
      PaymentGateway gateway,
      PaymentRepository intents,
      PaymentEventRepository events,
      PaymentSettlementService settlement,
      HandleChargebackService chargebacks,
      ObjectMapper objectMapper,
      IdGenerator ids,
      Clock clock) {
    this.gateway = gateway;
    this.intents = intents;
    this.events = events;
    this.settlement = settlement;
    this.chargebacks = chargebacks;
    this.objectMapper = objectMapper;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  @Transactional
  public WebhookResult handle(Provider provider, String signature, byte[] rawBody) {
    if (rawBody == null || !gateway.verifySignature(provider, signature, rawBody)) {
      throw new WebhookSignatureException();
    }

    WebhookPayload payload = parse(rawBody);
    HandleChargebackService.Outcome chargeback = chargebackOutcomeOf(payload.status());
    PaymentEventType type = chargeback == null ? parseType(payload.status()) : PaymentEventType.PENDING;

    PaymentIntent intent = intents.findByProviderRef(payload.providerRef()).orElse(null);
    if (intent == null) {
      // Unknown/untrusted ref — accept and ignore (202) so the provider does not retry-storm.
      return WebhookResult.IGNORED_UNKNOWN;
    }

    PaymentEvent event =
        PaymentEvent.record(
            ids.newId(),
            intent.getId(),
            payload.eventId(),
            type,
            new String(rawBody, StandardCharsets.UTF_8),
            clock.now());
    boolean fresh = events.recordEvent(event);
    if (!fresh) {
      // Replayed webhook (providerEventId already seen) — no-op, no re-transition, no second event.
      return WebhookResult.DUPLICATE;
    }

    if (chargeback != null) {
      // Provider chargeback event (signature-verified — this is the ONLY refund-driving path outside
      // admin adjudication). A LOST case forces a refund (clawback + ownership revocation, INV-9). The
      // dispute is keyed idempotently on the provider case id, and the refund clawback is exactly-once
      // (ledger_posting + uq_refund_per_dispute), so a re-delivery never double-clawbacks/revokes.
      String caseId = isBlank(payload.caseId()) ? payload.eventId() : payload.caseId();
      chargebacks.handle(intent, caseId, chargeback, payload.reason());
      return WebhookResult.HANDLED;
    }

    switch (type) {
      case SETTLED -> settlement.settle(intent.getId(), intent.getProviderRef());
      case FAILED -> settlement.fail(intent.getId(), reasonOr(payload.reason(), "declined"));
      case PENDING -> {
        // Provider reports still-processing: the event is recorded but the intent stays pending.
      }
    }
    return WebhookResult.HANDLED;
  }

  /**
   * Map a webhook status to a chargeback {@link HandleChargebackService.Outcome}, or {@code null} if
   * the status is a settlement (settled/failed/pending) rather than a chargeback event.
   */
  private static HandleChargebackService.Outcome chargebackOutcomeOf(String status) {
    return switch (status.trim().toLowerCase()) {
      case "chargeback", "chargeback_open", "dispute_opened" -> HandleChargebackService.Outcome.OPEN;
      case "chargeback_lost", "dispute_lost" -> HandleChargebackService.Outcome.LOST;
      case "chargeback_won", "dispute_won" -> HandleChargebackService.Outcome.WON;
      default -> null;
    };
  }

  private WebhookPayload parse(byte[] rawBody) {
    WebhookPayload payload;
    try {
      payload = objectMapper.readValue(rawBody, WebhookPayload.class);
    } catch (Exception e) {
      throw new ValidationException("malformed webhook payload");
    }
    if (payload == null
        || isBlank(payload.eventId())
        || isBlank(payload.providerRef())
        || isBlank(payload.status())) {
      throw new ValidationException("webhook payload missing eventId, providerRef, or status");
    }
    return payload;
  }

  private static PaymentEventType parseType(String status) {
    return switch (status.trim().toLowerCase()) {
      case "settled" -> PaymentEventType.SETTLED;
      case "failed" -> PaymentEventType.FAILED;
      case "pending" -> PaymentEventType.PENDING;
      default -> throw new ValidationException("unsupported webhook status: " + status, "status");
    };
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String reasonOr(String reason, String fallback) {
    return isBlank(reason) ? fallback : reason;
  }

  /**
   * Canonical inbound webhook shape the sandbox rail (and the real per-provider adapters, once their
   * anti-corruption mapping lands) normalise to. Unknown JSON fields are ignored by the managed
   * {@link ObjectMapper} (Quarkus disables fail-on-unknown-properties).
   */
  public record WebhookPayload(
      String eventId, String providerRef, String status, String reason, String caseId) {}
}
