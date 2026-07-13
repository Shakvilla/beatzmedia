package org.shakvilla.beatzmedia.payments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Unit tests for the WU-PAY-4 payout domain value objects and aggregates (framework-free): KYC/
 * withdrawal status semantics, the "first method is default" + card-is-not-a-destination rules, and
 * the withdrawal PAID transition guard (INV-6).
 */
@Tag("unit")
class PayoutDomainTest {

  private static final Instant T = Instant.parse("2026-07-04T00:00:00Z");

  private static Money ghs(long minor) {
    return Money.ofMinor(minor, Currency.GHS);
  }

  @Test
  void kyc_status_only_verified_is_verified() {
    assertTrue(KycStatus.VERIFIED.isVerified());
    assertFalse(KycStatus.NONE.isVerified());
    assertFalse(KycStatus.PENDING.isVerified());
    assertFalse(KycStatus.REJECTED.isVerified());
    assertEquals(KycStatus.NONE, KycStatus.fromWire(null));
    assertEquals(KycStatus.VERIFIED, KycStatus.fromWire("verified"));
  }

  @Test
  void withdrawal_status_payable_only_pending_or_ready() {
    assertTrue(WithdrawalStatus.PENDING.isPayable());
    assertTrue(WithdrawalStatus.READY.isPayable());
    assertFalse(WithdrawalStatus.PAID.isPayable());
    assertFalse(WithdrawalStatus.FAILED.isPayable());
  }

  @Test
  void payout_method_rejects_a_card_destination() {
    // A card cannot be a payout destination: there is no card PayoutDestination, and reconstitute
    // rejects the card kind (the domain invariant behind the service's 422).
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PayoutMethod.reconstitute(
                new org.shakvilla.beatzmedia.payments.domain.PayoutMethodId("m1"),
                new AccountId("a"),
                MethodKind.card,
                "Visa",
                "****",
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                T));
  }

  @Test
  void payout_method_default_toggles() {
    PayoutMethod m =
        PayoutMethod.create(
            "m1",
            new AccountId("a"),
            "MTN",
            "024...",
            new org.shakvilla.beatzmedia.payments.domain.PayoutDestination.Momo(
                org.shakvilla.beatzmedia.payments.domain.Provider.mtn, "0244000000"),
            false,
            T);
    assertFalse(m.isDefault());
    m.makeDefault();
    assertTrue(m.isDefault());
    m.clearDefault();
    assertFalse(m.isDefault());
  }

  @Test
  void withdrawal_marks_paid_from_payable_then_rejects_double_pay() {
    WithdrawalRequest w =
        WithdrawalRequest.reserved(
            "w1",
            new AccountId("a"),
            ghs(2000),
            ghs(100),
            new PayoutMethodId("m1"),
            new TxnId("t1"),
            new IdempotencyKey("k1"),
            T);
    assertEquals(WithdrawalStatus.PENDING, w.getStatus());
    w.markPaid();
    assertEquals(WithdrawalStatus.PAID, w.getStatus());
    // A second markPaid is an illegal transition (INV-6 in-app backstop).
    assertThrows(IllegalTransitionException.class, w::markPaid);
  }

  @Test
  void withdrawal_rejects_non_positive_amount() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            WithdrawalRequest.reserved(
                "w1", new AccountId("a"), ghs(0), ghs(0), new PayoutMethodId("m1"),
                new TxnId("t1"), new IdempotencyKey("k1"), T));
  }

  @Test
  void payout_batch_tallies_payments() {
    PayoutBatch b = PayoutBatch.start("b1", PayoutBatchKind.WEEKLY, "admin", T);
    b.recordPayment(1000);
    b.recordPayment(500);
    assertEquals(2, b.getCount());
    assertEquals(1500, b.getTotalMinor());
  }
}
