package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.payments.application.port.in.PaymentIntentView;
import org.shakvilla.beatzmedia.payments.application.service.InitiateChargeService;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyConflictException;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.ProviderException;
import org.shakvilla.beatzmedia.payments.fakes.FakePaymentGateway;
import org.shakvilla.beatzmedia.payments.fakes.FakePaymentRepository;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link InitiateChargeService} (LLFR-PAYMENTS-01.1). Uses in-memory fakes; no
 * framework. Focuses on the idempotency matrix (the crux of WU-PAY-1) and the provider-error path.
 */
@Tag("unit")
class InitiateChargeServiceTest {

  private FakePaymentRepository repo;
  private FakePaymentGateway gateway;
  private FakeAuditWriter audit;
  private InitiateChargeService service;

  private static final OrderRef ORDER = new OrderRef("BZ-2026-00001");
  private static final Money TEN_CEDIS = Money.ofMinor(1000, Currency.GHS);
  private static final PaymentMethodRef MTN_MOMO =
      new PaymentMethodRef(Provider.mtn, MethodKind.momo, "tok-123");

  @BeforeEach
  void setUp() {
    repo = new FakePaymentRepository();
    gateway = new FakePaymentGateway();
    audit = new FakeAuditWriter();
    service =
        new InitiateChargeService(
            repo, gateway, FakeIds.sequential("pi"), FakeClock.fixed(), audit);
  }

  @Test
  void initiates_a_pending_intent_and_audits() {
    PaymentIntentView view =
        service.charge(ORDER, TEN_CEDIS, MTN_MOMO, new IdempotencyKey("idem-1"));

    assertEquals("pending", view.status());
    assertEquals("BZ-2026-00001", view.orderRef());
    assertEquals("mtn", view.provider());
    assertEquals(0, view.amount().amount().compareTo(new java.math.BigDecimal("10.00")));
    assertEquals(1, gateway.initiateCalls());
    assertEquals(1, repo.count());
    assertEquals(1, audit.size(), "exactly one AuditEntry per mutation (INV-10)");
    assertEquals("FINANCE", audit.all().get(0).getType().name());
  }

  /** AC: same idempotency key twice -> exactly one provider charge and one intent. */
  @Test
  void same_key_same_body_returns_same_intent_no_double_charge() {
    PaymentIntentView first =
        service.charge(ORDER, TEN_CEDIS, MTN_MOMO, new IdempotencyKey("idem-1"));
    PaymentIntentView second =
        service.charge(ORDER, TEN_CEDIS, MTN_MOMO, new IdempotencyKey("idem-1"));

    assertEquals(first.id(), second.id());
    assertEquals(1, gateway.initiateCalls(), "no second provider charge on replay");
    assertEquals(1, repo.count(), "exactly one intent");
    assertEquals(1, audit.size(), "replay does not append a second audit entry");
  }

  /** AC: same key, different body -> 409 IDEMPOTENCY_KEY_CONFLICT. */
  @Test
  void same_key_different_body_conflicts() {
    service.charge(ORDER, TEN_CEDIS, MTN_MOMO, new IdempotencyKey("idem-1"));

    Money differentAmount = Money.ofMinor(2000, Currency.GHS);
    assertThrows(
        IdempotencyConflictException.class,
        () -> service.charge(ORDER, differentAmount, MTN_MOMO, new IdempotencyKey("idem-1")));

    assertEquals(1, gateway.initiateCalls(), "conflicting replay never charges the provider");
    assertEquals(1, repo.count());
  }

  @Test
  void different_keys_create_distinct_intents() {
    PaymentIntentView a =
        service.charge(ORDER, TEN_CEDIS, MTN_MOMO, new IdempotencyKey("idem-1"));
    PaymentIntentView b =
        service.charge(ORDER, TEN_CEDIS, MTN_MOMO, new IdempotencyKey("idem-2"));

    assertTrue(!a.id().equals(b.id()));
    assertEquals(2, gateway.initiateCalls());
    assertEquals(2, repo.count());
  }

  /** A differing payment token for the same money-affecting fields must NOT conflict. */
  @Test
  void differing_token_same_money_fields_does_not_conflict() {
    service.charge(ORDER, TEN_CEDIS, MTN_MOMO, new IdempotencyKey("idem-1"));
    PaymentMethodRef reissuedToken =
        new PaymentMethodRef(Provider.mtn, MethodKind.momo, "tok-REISSUED");

    PaymentIntentView replay =
        service.charge(ORDER, TEN_CEDIS, reissuedToken, new IdempotencyKey("idem-1"));

    assertEquals("pending", replay.status());
    assertEquals(1, repo.count());
    assertEquals(1, gateway.initiateCalls());
  }

  /** Provider error -> failed intent persisted, key consumed, ProviderException surfaced. */
  @Test
  void provider_error_persists_failed_intent_and_rethrows() {
    gateway.failOnNextInitiate();

    assertThrows(
        ProviderException.class,
        () -> service.charge(ORDER, TEN_CEDIS, MTN_MOMO, new IdempotencyKey("idem-1")));

    assertEquals(1, repo.count(), "failed intent persisted so key is consumed");
    assertEquals(1, audit.size(), "failure is audited");
    // Replay with same key + same body returns the failed intent, no second charge.
    PaymentIntentView replay =
        service.charge(ORDER, TEN_CEDIS, MTN_MOMO, new IdempotencyKey("idem-1"));
    assertEquals("failed", replay.status());
    assertEquals(1, gateway.initiateCalls(), "no re-charge on replay of a failed intent");
  }
}
