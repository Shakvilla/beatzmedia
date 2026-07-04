package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.in.WebhookResult;
import org.shakvilla.beatzmedia.payments.application.service.HandleProviderWebhookService;
import org.shakvilla.beatzmedia.payments.application.service.PaymentSettlementService;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentFailed;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntentStatus;
import org.shakvilla.beatzmedia.payments.domain.PaymentSettled;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.WebhookSignatureException;
import org.shakvilla.beatzmedia.payments.fakes.FakePaymentEventRepository;
import org.shakvilla.beatzmedia.payments.fakes.FakePaymentGateway;
import org.shakvilla.beatzmedia.payments.fakes.FakePaymentRepository;
import org.shakvilla.beatzmedia.payments.fakes.RecordingEvent;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link HandleProviderWebhookService} (LLFR-PAYMENTS-01.2) with hand-written fakes.
 * Covers the signature gate (401), unknown-ref (202), idempotent duplicate (200 no-op), and the
 * settle/fail transitions with exactly-once event emission.
 */
@Tag("unit")
class HandleProviderWebhookServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");
  private static final String REF = "MTN-1";

  private FakePaymentGateway gateway;
  private FakePaymentRepository repo;
  private FakePaymentEventRepository events;
  private RecordingEvent<PaymentSettled> settledEvents;
  private RecordingEvent<PaymentFailed> failedEvents;
  private HandleProviderWebhookService service;

  @BeforeEach
  void setUp() {
    gateway = new FakePaymentGateway();
    repo = new FakePaymentRepository();
    events = new FakePaymentEventRepository();
    settledEvents = new RecordingEvent<>();
    failedEvents = new RecordingEvent<>();
    FakeClock clock = FakeClock.at(NOW);
    PaymentSettlementService settlement =
        new PaymentSettlementService(repo, clock, settledEvents, failedEvents);
    // The chargeback path (WU-PAY-5) is exercised by RefundDisputeIT end-to-end; these unit tests
    // cover only settle/fail/duplicate/signature, whose statuses never route to the chargeback service,
    // so a null collaborator is safe here (it is only dereferenced for chargeback_* statuses).
    service =
        new HandleProviderWebhookService(
            gateway, repo, events, settlement, null, new ObjectMapper(), FakeIds.sequential("ev"),
            clock);
  }

  @Test
  void invalid_signature_is_rejected_before_any_side_effect() {
    seedPending("pi-1", REF);
    gateway.setSignatureValid(false);

    assertThrows(
        WebhookSignatureException.class,
        () -> service.handle(Provider.mtn, "bad-sig", body("ev-1", REF, "settled")));
    assertEquals(0, events.count());
    assertEquals(PaymentIntentStatus.pending, repo.findById("pi-1").orElseThrow().getStatus());
  }

  @Test
  void settled_webhook_settles_intent_and_emits_one_event() {
    seedPending("pi-1", REF);

    WebhookResult result = service.handle(Provider.mtn, "sig", body("ev-1", REF, "settled"));

    assertEquals(WebhookResult.HANDLED, result);
    assertEquals(PaymentIntentStatus.settled, repo.findById("pi-1").orElseThrow().getStatus());
    assertEquals(1, settledEvents.count());
    assertEquals(1, events.count());
  }

  @Test
  void duplicate_webhook_transitions_at_most_once_and_emits_one_event() {
    seedPending("pi-1", REF);

    WebhookResult first = service.handle(Provider.mtn, "sig", body("ev-1", REF, "settled"));
    WebhookResult second = service.handle(Provider.mtn, "sig", body("ev-1", REF, "settled"));

    assertEquals(WebhookResult.HANDLED, first);
    assertEquals(WebhookResult.DUPLICATE, second);
    assertEquals(1, settledEvents.count()); // exactly one PaymentSettled (AC 01.2)
    assertEquals(1, events.count());
    assertEquals(PaymentIntentStatus.settled, repo.findById("pi-1").orElseThrow().getStatus());
  }

  @Test
  void failed_webhook_fails_intent_and_emits_one_event() {
    seedPending("pi-1", REF);

    WebhookResult result = service.handle(Provider.mtn, "sig", body("ev-1", REF, "failed"));

    assertEquals(WebhookResult.HANDLED, result);
    PaymentIntent after = repo.findById("pi-1").orElseThrow();
    assertEquals(PaymentIntentStatus.failed, after.getStatus());
    assertEquals(1, failedEvents.count());
  }

  @Test
  void unknown_ref_is_accepted_and_ignored() {
    // no intent seeded for this ref
    WebhookResult result = service.handle(Provider.mtn, "sig", body("ev-1", "MTN-UNKNOWN", "settled"));

    assertEquals(WebhookResult.IGNORED_UNKNOWN, result);
    assertEquals(0, events.count());
    assertEquals(0, settledEvents.count());
  }

  @Test
  void malformed_payload_is_a_validation_error() {
    assertThrows(
        ValidationException.class,
        () -> service.handle(Provider.mtn, "sig", "not json".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void payload_missing_required_field_is_a_validation_error() {
    assertThrows(
        ValidationException.class,
        () ->
            service.handle(
                Provider.mtn,
                "sig",
                "{\"providerRef\":\"MTN-1\",\"status\":\"settled\"}"
                    .getBytes(StandardCharsets.UTF_8)));
  }

  // ---- helpers ----------------------------------------------------------

  private void seedPending(String id, String providerRef) {
    repo.seed(
        PaymentIntent.reconstitute(
            id,
            new AccountId("acct-1"),
            new OrderRef("BZ-2026-1"),
            Money.ofMinor(1000, Currency.GHS),
            Provider.mtn,
            MethodKind.momo,
            providerRef,
            PaymentIntentStatus.pending,
            null,
            "idem-" + id,
            "fp-" + id,
            NOW,
            NOW));
  }

  private static byte[] body(String eventId, String providerRef, String status) {
    return ("{\"eventId\":\"" + eventId + "\",\"providerRef\":\"" + providerRef + "\",\"status\":\""
            + status + "\"}")
        .getBytes(StandardCharsets.UTF_8);
  }
}
