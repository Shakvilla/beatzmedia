package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.in.WebhookResult;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway.ProviderStatus;
import org.shakvilla.beatzmedia.payments.application.service.HandleReddeReceiptService;
import org.shakvilla.beatzmedia.payments.application.service.PaymentSettlementService;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentFailed;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntentStatus;
import org.shakvilla.beatzmedia.payments.domain.PaymentSettled;
import org.shakvilla.beatzmedia.payments.domain.Provider;
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
 * Unit tests for {@link HandleReddeReceiptService} (WU-PAY-6, ADR-28): settlement is driven by the
 * authenticated {@code queryStatus} pull-back, not the callback body; idempotent, and defers to the
 * recon poll when the pull-back is unavailable.
 */
@Tag("unit")
class HandleReddeReceiptServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");
  private static final String TXID = "103046";

  private FakePaymentGateway gateway;
  private FakePaymentRepository repo;
  private FakePaymentEventRepository events;
  private RecordingEvent<PaymentSettled> settled;
  private RecordingEvent<PaymentFailed> failed;
  private HandleReddeReceiptService service;

  @BeforeEach
  void setUp() {
    gateway = new FakePaymentGateway();
    repo = new FakePaymentRepository();
    events = new FakePaymentEventRepository();
    settled = new RecordingEvent<>();
    failed = new RecordingEvent<>();
    FakeClock clock = FakeClock.at(NOW);
    PaymentSettlementService settlement =
        new PaymentSettlementService(repo, clock, settled, failed);
    service =
        new HandleReddeReceiptService(
            gateway, repo, events, settlement, new ObjectMapper(), FakeIds.sequential("ev"), clock);
  }

  private void seedPendingIntent() {
    repo.seed(
        PaymentIntent.reconstitute(
            "pi-1",
            new AccountId("acct-1"),
            new OrderRef("BZ-2026-1"),
            Money.ofMinor(1000, Currency.GHS),
            Provider.mtn,
            MethodKind.momo,
            TXID,
            PaymentIntentStatus.pending,
            null,
            null,
            "idem-1",
            "fp-1",
            NOW,
            NOW));
  }

  private static byte[] callback(String status) {
    return ("{\"transactionid\":\"" + TXID + "\",\"status\":\"" + status + "\"}").getBytes();
  }

  @Test
  void settlesOnPulledPaidRegardlessOfBodyClaim() {
    seedPendingIntent();
    gateway.setStatus(TXID, ProviderStatus.settled());

    // Body claims only PROGRESS, but the pull-back says PAID → settle.
    WebhookResult result = service.handle(callback("PROGRESS"));

    assertEquals(WebhookResult.HANDLED, result);
    assertEquals(PaymentIntentStatus.settled, repo.findById("pi-1").orElseThrow().getStatus());
    assertEquals(1, settled.fired().size());
    assertEquals(1, events.count());
  }

  @Test
  void failsOnPulledFailed() {
    seedPendingIntent();
    gateway.setStatus(TXID, ProviderStatus.failed("insufficient funds"));

    assertEquals(WebhookResult.HANDLED, service.handle(callback("FAILED")));
    assertEquals(PaymentIntentStatus.failed, repo.findById("pi-1").orElseThrow().getStatus());
    assertEquals(1, failed.fired().size());
  }

  @Test
  void pendingPullBackIsNoOp() {
    seedPendingIntent();
    gateway.setStatus(TXID, ProviderStatus.pending());

    assertEquals(WebhookResult.HANDLED, service.handle(callback("PROGRESS")));
    assertEquals(PaymentIntentStatus.pending, repo.findById("pi-1").orElseThrow().getStatus());
    assertEquals(0, events.count());
    assertEquals(0, settled.fired().size());
  }

  @Test
  void unknownTransactionIdIsIgnored() {
    // No intent seeded → 202, and the gateway is never pulled.
    assertEquals(WebhookResult.IGNORED_UNKNOWN, service.handle(callback("PAID")));
    assertEquals(0, gateway.queryStatusCalls());
  }

  @Test
  void duplicateDeliveryIsNoOp() {
    seedPendingIntent();
    gateway.setStatus(TXID, ProviderStatus.settled());

    assertEquals(WebhookResult.HANDLED, service.handle(callback("PAID")));
    assertEquals(WebhookResult.DUPLICATE, service.handle(callback("PAID")));
    assertEquals(1, events.count());
    assertEquals(1, settled.fired().size()); // settled exactly once
  }

  @Test
  void pullBackFailureDefersToRecon() {
    seedPendingIntent();
    gateway.failQueryStatus();

    assertEquals(WebhookResult.IGNORED_UNKNOWN, service.handle(callback("PAID")));
    assertEquals(PaymentIntentStatus.pending, repo.findById("pi-1").orElseThrow().getStatus());
    assertEquals(0, events.count());
  }

  @Test
  void missingTransactionIdIsRejected() {
    assertThrows(ValidationException.class, () -> service.handle("{}".getBytes()));
  }

  @Test
  void malformedBodyIsRejected() {
    assertTrue(
        assertThrows(ValidationException.class, () -> service.handle("not json".getBytes()))
            .getMessage()
            .toLowerCase()
            .contains("malformed"));
  }
}
