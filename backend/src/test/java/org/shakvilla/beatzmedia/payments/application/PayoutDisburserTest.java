package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.payments.application.service.PayoutDisburser;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutDestination;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.payments.domain.PayoutTxn;
import org.shakvilla.beatzmedia.payments.domain.PayoutTxnStatus;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalRequest;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalStatus;
import org.shakvilla.beatzmedia.payments.fakes.FakeKycProvider;
import org.shakvilla.beatzmedia.payments.fakes.FakeLedgerRepository;
import org.shakvilla.beatzmedia.payments.fakes.FakePaymentGateway;
import org.shakvilla.beatzmedia.payments.fakes.FakePayoutRepository;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link PayoutDisburser}'s WU-PAY-7 sync/async branch. The sandbox gateway
 * (confirmsDisbursementAsync=false) keeps the WU-PAY-4 behaviour byte-for-byte; the Redde gateway
 * (true) sends the cashout and goes SENT with NO ledger posting, deferring the money movement to the
 * cashout webhook (INV-6). A rejected/legacy send marks the withdrawal failed without posting.
 */
@Tag("unit")
class PayoutDisburserTest {

  private static final AccountId CREATOR = new AccountId("creator-1");
  private static final PayoutMethodId METHOD = new PayoutMethodId("pm-1");
  private static final String ADMIN = "admin-1";

  private FakePayoutRepository payouts;
  private FakeLedgerRepository ledger;
  private FakeKycProvider kyc;
  private FakeAuditWriter audit;
  private FakePaymentGateway gateway;
  private PayoutDisburser disburser;

  @BeforeEach
  void setUp() {
    payouts = new FakePayoutRepository();
    ledger = new FakeLedgerRepository();
    kyc = new FakeKycProvider();
    audit = new FakeAuditWriter();
    gateway = new FakePaymentGateway();
    disburser =
        new PayoutDisburser(
            payouts, ledger, kyc, gateway, FakeIds.sequential("po"), FakeClock.fixed(), audit);
    kyc.verify(CREATOR);
  }

  private static Money ghs(long minor) {
    return Money.ofMinor(minor, Currency.GHS);
  }

  private void seedMethod(PayoutDestination destination) {
    payouts.saveMethod(
        PayoutMethod.create(METHOD.value(), CREATOR, "MoMo", "024...", destination, true,
            FakeClock.fixed().now()));
  }

  /** Seed a legacy momo method (no structured destination) directly via reconstitute. */
  private void seedLegacyMethod() {
    payouts.saveMethod(
        PayoutMethod.reconstitute(
            METHOD, CREATOR, MethodKind.momo, "MoMo", "024...", null, null, null, null, null, null,
            true, FakeClock.fixed().now()));
  }

  private WithdrawalId seedWithdrawal(String id, long minor) {
    WithdrawalRequest w =
        WithdrawalRequest.reserved(
            id, CREATOR, ghs(minor), ghs(100), METHOD, new TxnId("reserve-" + id),
            new IdempotencyKey("idem-" + id), FakeClock.fixed().now());
    payouts.saveWithdrawal(w);
    return new WithdrawalId(id);
  }

  private long payoutLedgerEntries() {
    return ledger.entries.stream().filter(e -> "payout".equals(e.getRefType())).count();
  }

  @Test
  void sync_path_posts_ledger_and_marks_paid_without_calling_the_gateway() {
    seedMethod(new PayoutDestination.Momo(Provider.mtn, "0244009210"));
    WithdrawalId wid = seedWithdrawal("w1", 5000);

    Optional<PayoutTxn> txn = disburser.disburseOne("b1", wid, ADMIN, false);

    assertTrue(txn.isPresent());
    assertEquals(WithdrawalStatus.PAID, payouts.findWithdrawal(wid).get().getStatus());
    assertEquals(PayoutTxnStatus.PAID, payouts.txnFor(wid).get().getStatus());
    assertTrue(payoutLedgerEntries() > 0, "sync path posts the disbursement now");
    assertEquals(0, gateway.disburseCalls(), "sandbox path never calls the rail");
    assertEquals("EXECUTE_PAYOUT", audit.all().get(0).getAction());
  }

  @Test
  void async_path_sends_and_goes_sent_with_no_ledger_posting() {
    gateway.setConfirmsDisbursementAsync(true);
    seedMethod(new PayoutDestination.Momo(Provider.mtn, "0244009210"));
    WithdrawalId wid = seedWithdrawal("w2", 5000);

    Optional<PayoutTxn> txn = disburser.disburseOne("b1", wid, ADMIN, false);

    assertTrue(txn.isPresent());
    assertEquals(WithdrawalStatus.SENT, payouts.findWithdrawal(wid).get().getStatus());
    assertEquals(PayoutTxnStatus.SENT, payouts.txnFor(wid).get().getStatus());
    assertEquals(0, payoutLedgerEntries(), "async path defers the ledger to the cashout webhook");
    assertEquals(1, gateway.disburseCalls());
    assertTrue(gateway.disburseDestinations().get(0) instanceof PayoutDestination.Momo);
    assertEquals("SEND_PAYOUT", audit.all().get(0).getAction());
  }

  @Test
  void async_send_rejected_marks_failed_and_posts_nothing() {
    gateway.setConfirmsDisbursementAsync(true);
    gateway.failOnDisburse();
    seedMethod(new PayoutDestination.Momo(Provider.mtn, "0244009210"));
    WithdrawalId wid = seedWithdrawal("w3", 5000);

    Optional<PayoutTxn> txn = disburser.disburseOne("b1", wid, ADMIN, false);

    assertFalse(txn.isPresent());
    assertEquals(WithdrawalStatus.FAILED, payouts.findWithdrawal(wid).get().getStatus());
    assertEquals(0, payoutLedgerEntries());
    assertEquals("FAIL_PAYOUT", audit.all().get(0).getAction());
  }

  @Test
  void async_legacy_method_without_a_structured_destination_fails_closed() {
    gateway.setConfirmsDisbursementAsync(true);
    seedLegacyMethod();
    WithdrawalId wid = seedWithdrawal("w4", 5000);

    Optional<PayoutTxn> txn = disburser.disburseOne("b1", wid, ADMIN, false);

    assertFalse(txn.isPresent());
    assertEquals(WithdrawalStatus.FAILED, payouts.findWithdrawal(wid).get().getStatus());
    assertEquals(0, gateway.disburseCalls(), "never send an under-specified cashout");
  }

  @Test
  void already_sent_withdrawal_is_a_no_op() {
    gateway.setConfirmsDisbursementAsync(true);
    seedMethod(new PayoutDestination.Momo(Provider.mtn, "0244009210"));
    // A withdrawal already SENT (not payable) must not be re-sent.
    WithdrawalRequest w =
        WithdrawalRequest.reconstitute(
            new WithdrawalId("w5"), CREATOR, ghs(5000), ghs(100), METHOD,
            WithdrawalStatus.SENT, new TxnId("r5"), new IdempotencyKey("i5"), Instant.EPOCH);
    payouts.saveWithdrawal(w);

    Optional<PayoutTxn> txn = disburser.disburseOne("b1", new WithdrawalId("w5"), ADMIN, false);

    assertFalse(txn.isPresent());
    assertEquals(0, gateway.disburseCalls());
  }
}
