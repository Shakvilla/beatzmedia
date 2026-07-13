package org.shakvilla.beatzmedia.payments.application.service;

import java.nio.charset.StandardCharsets;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.payments.application.port.in.HandleReddeReceipt;
import org.shakvilla.beatzmedia.payments.application.port.in.WebhookResult;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentEventRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway.ProviderStatus;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentRepository;
import org.shakvilla.beatzmedia.payments.domain.PaymentEvent;
import org.shakvilla.beatzmedia.payments.domain.PaymentEventType;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;
import org.shakvilla.beatzmedia.payments.domain.ProviderException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Handles Redde receive-callback notifications (LLFR-PAYMENTS-06.1, {@link HandleReddeReceipt}).
 *
 * <p><strong>Verify by pull-back (ADR-28):</strong> Redde signs nothing, so the callback body is
 * treated as a mere hint. Flow (one transaction):
 *
 * <ol>
 *   <li>Extract {@code transactionid} from the raw body; a body without one is a {@code 422}.
 *   <li>Resolve the intent by {@code providerRef == transactionid}; unknown → {@link
 *       WebhookResult#IGNORED_UNKNOWN} (202).
 *   <li>Pull the <em>authenticated</em> truth: {@code gateway.queryStatus(intent.provider,
 *       transactionid)} (Redde's {@code GET /v1/status}, keyed on our apikey). A transient failure →
 *       202 (the 30s recon poll will settle it later — no retry storm).
 *   <li>On a terminal pulled outcome (settled/failed), record a {@code payment_event} keyed on
 *       {@code redde:transactionid} (UNIQUE idempotency backstop) and transition via {@link
 *       PaymentSettlementService}. A pending pull is a no-op. A duplicate is a 200 no-op.
 * </ol>
 *
 * The transition is driven by the <em>pulled</em> status, never the body's claim, so a forged
 * callback can at most trigger a re-confirmation of the real Redde state — never a fake settlement.
 */
@ApplicationScoped
public class HandleReddeReceiptService implements HandleReddeReceipt {

  private static final Logger LOG = Logger.getLogger(HandleReddeReceiptService.class);

  private final PaymentGateway gateway;
  private final PaymentRepository intents;
  private final PaymentEventRepository events;
  private final PaymentSettlementService settlement;
  private final ObjectMapper objectMapper;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public HandleReddeReceiptService(
      PaymentGateway gateway,
      PaymentRepository intents,
      PaymentEventRepository events,
      PaymentSettlementService settlement,
      ObjectMapper objectMapper,
      IdGenerator ids,
      Clock clock) {
    this.gateway = gateway;
    this.intents = intents;
    this.events = events;
    this.settlement = settlement;
    this.objectMapper = objectMapper;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  @Transactional
  public WebhookResult handle(byte[] rawBody) {
    ReddeCallback callback = parse(rawBody);

    PaymentIntent intent = intents.findByProviderRef(callback.transactionId()).orElse(null);
    if (intent == null) {
      return WebhookResult.IGNORED_UNKNOWN;
    }

    ProviderStatus pulled;
    try {
      pulled = gateway.queryStatus(intent.getProvider(), callback.transactionId());
    } catch (ProviderException e) {
      // Could not authenticate the callback right now — do not settle off an unverified body. The
      // recon poll (queryStatus every 30s) will settle it once Redde is reachable. 202, no retry storm.
      LOG.warnf(
          "Redde receipt pull-back failed for intent %s (txid %s): %s — leaving to recon",
          intent.getId(), callback.transactionId(), e.getMessage());
      return WebhookResult.IGNORED_UNKNOWN;
    }

    PaymentEventType outcome = pulled.outcome();
    if (outcome == PaymentEventType.PENDING) {
      // A PROGRESS/PENDING notification — nothing to transition yet.
      return WebhookResult.HANDLED;
    }

    // Terminal outcome: idempotently record (redde:<transactionid> is unique) then transition.
    PaymentEvent event =
        PaymentEvent.record(
            ids.newId(),
            intent.getId(),
            "redde:" + callback.transactionId(),
            outcome,
            new String(rawBody, StandardCharsets.UTF_8),
            clock.now());
    if (!events.recordEvent(event)) {
      return WebhookResult.DUPLICATE;
    }

    switch (outcome) {
      case SETTLED -> settlement.settle(intent.getId(), intent.getProviderRef());
      case FAILED -> settlement.fail(intent.getId(), reasonOr(pulled.reason(), "declined"));
      case PENDING -> {
        // unreachable (guarded above)
      }
    }
    return WebhookResult.HANDLED;
  }

  private ReddeCallback parse(byte[] rawBody) {
    if (rawBody == null) {
      throw new ValidationException("empty Redde callback body");
    }
    String transactionId;
    try {
      JsonNode node = objectMapper.readTree(rawBody);
      JsonNode txid = node.get("transactionid");
      transactionId = txid == null || txid.isNull() ? null : txid.asText();
    } catch (Exception e) {
      throw new ValidationException("malformed Redde callback payload");
    }
    if (transactionId == null || transactionId.isBlank()) {
      throw new ValidationException("Redde callback missing transactionid", "transactionid");
    }
    return new ReddeCallback(transactionId);
  }

  private static String reasonOr(String reason, String fallback) {
    return reason == null || reason.isBlank() ? fallback : reason;
  }

  /** The single field we take from the (untrusted) Redde callback body — which txn to pull back. */
  private record ReddeCallback(String transactionId) {}
}
