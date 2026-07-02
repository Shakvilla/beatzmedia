package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.in.ReconciliationReport;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway.ProviderStatus;
import org.shakvilla.beatzmedia.payments.application.service.PaymentSettlementService;
import org.shakvilla.beatzmedia.payments.application.service.ReconcileService;
import org.shakvilla.beatzmedia.payments.application.service.ReconciliationStore;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.DiscrepancyKind;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentFailed;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntentStatus;
import org.shakvilla.beatzmedia.payments.domain.PaymentSettled;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.fakes.FakeDiscrepancyRepository;
import org.shakvilla.beatzmedia.payments.fakes.FakePaymentGateway;
import org.shakvilla.beatzmedia.payments.fakes.FakePaymentRepository;
import org.shakvilla.beatzmedia.payments.fakes.RecordingEvent;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link ReconcileService} — the timeout poll (LLFR-PAYMENTS-01.3) and daily
 * reconciliation (LLFR-PAYMENTS-01.4), driven entirely by hand-written fakes.
 */
@Tag("unit")
class ReconcileServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");

  private FakePaymentRepository repo;
  private FakePaymentGateway gateway;
  private FakeDiscrepancyRepository discrepancies;
  private RecordingEvent<PaymentSettled> settledEvents;
  private RecordingEvent<PaymentFailed> failedEvents;
  private FakeClock clock;
  private ReconcileService service;

  @BeforeEach
  void setUp() {
    repo = new FakePaymentRepository();
    gateway = new FakePaymentGateway();
    discrepancies = new FakeDiscrepancyRepository();
    settledEvents = new RecordingEvent<>();
    failedEvents = new RecordingEvent<>();
    clock = FakeClock.at(NOW);
    PaymentSettlementService settlement =
        new PaymentSettlementService(repo, clock, settledEvents, failedEvents);
    ReconciliationStore store = new ReconciliationStore(repo, discrepancies);
    service = new ReconcileService(store, gateway, settlement, FakeIds.sequential("disc"), clock);
  }

  // ---- timeout poll (01.3) ----------------------------------------------

  @Test
  void poll_times_out_a_pending_intent_past_the_max_window() {
    // Never-delivered webhook: provider still says PENDING, intent is older than the max window.
    PaymentIntent intent = seedPending("pi-1", "MTN-1", NOW.minus(Duration.ofHours(2)));

    service.pollPendingTimeouts(Duration.ofMinutes(5), Duration.ofMinutes(30));

    assertEquals(PaymentIntentStatus.timeout, repo.findById("pi-1").orElseThrow().getStatus());
    assertEquals(1, failedEvents.count());
    assertEquals("timeout", failedEvents.fired().get(0).reason());
    assertEquals(0, settledEvents.count());
    assertEquals("pi-1", intent.getId());
  }

  @Test
  void poll_settles_when_provider_reports_settled() {
    seedPending("pi-2", "MTN-2", NOW.minus(Duration.ofMinutes(10)));
    gateway.setStatus("MTN-2", ProviderStatus.settled());

    service.pollPendingTimeouts(Duration.ofMinutes(5), Duration.ofMinutes(30));

    assertEquals(PaymentIntentStatus.settled, repo.findById("pi-2").orElseThrow().getStatus());
    assertEquals(1, settledEvents.count());
    assertEquals(0, failedEvents.count());
  }

  @Test
  void poll_fails_when_provider_reports_failed() {
    seedPending("pi-3", "MTN-3", NOW.minus(Duration.ofMinutes(10)));
    gateway.setStatus("MTN-3", ProviderStatus.failed("declined"));

    service.pollPendingTimeouts(Duration.ofMinutes(5), Duration.ofMinutes(30));

    PaymentIntent after = repo.findById("pi-3").orElseThrow();
    assertEquals(PaymentIntentStatus.failed, after.getStatus());
    assertEquals("declined", after.getFailureReason());
    assertEquals(1, failedEvents.count());
  }

  @Test
  void poll_leaves_pending_within_the_window() {
    seedPending("pi-4", "MTN-4", NOW.minus(Duration.ofMinutes(10)));
    // provider default = PENDING; age (10m) < maxWindow (30m) → keep waiting.

    service.pollPendingTimeouts(Duration.ZERO, Duration.ofMinutes(30));

    assertEquals(PaymentIntentStatus.pending, repo.findById("pi-4").orElseThrow().getStatus());
    assertEquals(0, settledEvents.count());
    assertEquals(0, failedEvents.count());
  }

  @Test
  void poll_leaves_pending_when_the_rail_is_unreachable() {
    seedPending("pi-5", "MTN-5", NOW.minus(Duration.ofHours(2)));
    gateway.failQueryStatus(); // transient error even though the intent is past the max window

    service.pollPendingTimeouts(Duration.ZERO, Duration.ofMinutes(30));

    assertEquals(PaymentIntentStatus.pending, repo.findById("pi-5").orElseThrow().getStatus());
    assertEquals(0, failedEvents.count());
  }

  // ---- daily reconciliation (01.4) --------------------------------------

  @Test
  void reconcile_flags_provider_settled_but_intent_not_settled() {
    // AC 01.4: a provider-settled charge with no recorded settlement → a discrepancy.
    seedTerminal("pi-6", "MTN-6", PaymentIntentStatus.timeout, NOW.minus(Duration.ofHours(1)));
    gateway.setStatus("MTN-6", ProviderStatus.settled());

    ReconciliationReport report = service.reconcileDaily(LocalDate.parse("2026-07-02"));

    assertEquals(1, report.scanned());
    assertEquals(1, report.discrepancies());
    assertEquals(
        DiscrepancyKind.PROVIDER_SETTLED_INTENT_NOT, discrepancies.recorded().get(0).getKind());
  }

  @Test
  void reconcile_flags_provider_failed_but_intent_settled() {
    seedTerminal("pi-7", "MTN-7", PaymentIntentStatus.settled, NOW.minus(Duration.ofHours(1)));
    gateway.setStatus("MTN-7", ProviderStatus.failed("reversed"));

    ReconciliationReport report = service.reconcileDaily(LocalDate.parse("2026-07-02"));

    assertEquals(1, report.discrepancies());
    assertEquals(
        DiscrepancyKind.PROVIDER_FAILED_INTENT_SETTLED, discrepancies.recorded().get(0).getKind());
  }

  @Test
  void reconcile_records_nothing_when_records_agree() {
    seedTerminal("pi-8", "MTN-8", PaymentIntentStatus.settled, NOW.minus(Duration.ofHours(1)));
    gateway.setStatus("MTN-8", ProviderStatus.settled());

    ReconciliationReport report = service.reconcileDaily(LocalDate.parse("2026-07-02"));

    assertEquals(1, report.scanned());
    assertEquals(0, report.discrepancies());
  }

  @Test
  void reconcile_treats_pending_provider_as_inconclusive() {
    seedTerminal("pi-9", "MTN-9", PaymentIntentStatus.settled, NOW.minus(Duration.ofHours(1)));
    // provider default = PENDING → inconclusive, not a discrepancy.

    ReconciliationReport report = service.reconcileDaily(LocalDate.parse("2026-07-02"));

    assertEquals(0, report.discrepancies());
  }

  @Test
  void reconcile_is_idempotent_over_the_same_day() {
    seedTerminal("pi-10", "MTN-10", PaymentIntentStatus.timeout, NOW.minus(Duration.ofHours(1)));
    gateway.setStatus("MTN-10", ProviderStatus.settled());
    LocalDate day = LocalDate.parse("2026-07-02");

    assertEquals(1, service.reconcileDaily(day).discrepancies());
    assertEquals(0, service.reconcileDaily(day).discrepancies()); // already recorded
    assertEquals(1, discrepancies.count());
  }

  // ---- helpers ----------------------------------------------------------

  private PaymentIntent seedPending(String id, String providerRef, Instant createdAt) {
    return seedTerminal(id, providerRef, PaymentIntentStatus.pending, createdAt);
  }

  private PaymentIntent seedTerminal(
      String id, String providerRef, PaymentIntentStatus status, Instant createdAt) {
    PaymentIntent intent =
        PaymentIntent.reconstitute(
            id,
            new AccountId("acct-1"),
            new OrderRef("BZ-2026-1"),
            Money.ofMinor(1000, Currency.GHS),
            Provider.mtn,
            MethodKind.momo,
            providerRef,
            status,
            status == PaymentIntentStatus.timeout ? "timeout" : null,
            "idem-" + id,
            "fp-" + id,
            createdAt,
            createdAt);
    repo.seed(intent);
    return intent;
  }
}
