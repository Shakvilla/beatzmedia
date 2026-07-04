package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutBatchView;
import org.shakvilla.beatzmedia.payments.application.service.PayoutRunService;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.Direction;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.KycBlockedException;
import org.shakvilla.beatzmedia.payments.domain.LedgerEntry;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalRequest;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalStatus;
import org.shakvilla.beatzmedia.payments.fakes.FakeKycProvider;
import org.shakvilla.beatzmedia.payments.fakes.FakeLedgerRepository;
import org.shakvilla.beatzmedia.payments.fakes.FakePayoutRepository;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link PayoutRunService} — LLFR-PAYMENTS-03.3 (weekly run) and 03.4 (single send).
 * Proves: a payout posts a balanced disbursement (INV-6), a re-run does NOT double-debit (exactly-once
 * via uq_payout_per_withdrawal), a weekly run skips KYC-unverified creators, a single send blocks on
 * KYC with a mapped error (INV-8), and every payout is audited (INV-10).
 */
@Tag("unit")
class PayoutRunServiceTest {

  private static final String ADMIN = "admin-1";
  private FakePayoutRepository payouts;
  private FakeLedgerRepository ledger;
  private FakeKycProvider kyc;
  private FakeAuditWriter audit;
  private PayoutRunService service;

  @BeforeEach
  void setUp() {
    payouts = new FakePayoutRepository();
    ledger = new FakeLedgerRepository();
    kyc = new FakeKycProvider();
    audit = new FakeAuditWriter();
    service =
        new PayoutRunService(payouts, ledger, kyc, FakeIds.sequential("po"), FakeClock.fixed(), audit);
  }

  private static Money ghs(long minor) {
    return Money.ofMinor(minor, Currency.GHS);
  }

  /** Seed a pending, reserved withdrawal for a creator with a momo method. */
  private WithdrawalRequest seedWithdrawal(String id, AccountId creator, long minor) {
    payouts.saveMethod(
        PayoutMethod.create(
            "pm-" + creator.value(), creator, MethodKind.momo, "MoMo", "024...", true,
            FakeClock.fixed().now()));
    WithdrawalRequest w =
        WithdrawalRequest.reserved(
            id,
            creator,
            ghs(minor),
            ghs(100),
            new org.shakvilla.beatzmedia.payments.domain.PayoutMethodId("pm-" + creator.value()),
            new TxnId("reserve-" + id),
            new IdempotencyKey("wk-" + id),
            FakeClock.fixed().now());
    return payouts.saveWithdrawal(w);
  }

  @Test
  void single_send_posts_balanced_disbursement_and_marks_paid_and_audits() {
    AccountId creator = new AccountId("c1");
    kyc.verify(creator);
    seedWithdrawal("w1", creator, 5000);

    service.send(ADMIN, new WithdrawalId("w1"), new IdempotencyKey("run1"));

    // Disbursement is balanced (fake asserts). Assert the two rows.
    long debit = 0;
    long credit = 0;
    for (LedgerEntry e : ledger.entries) {
      if ("payout".equals(e.getRefType())) {
        if (e.getDirection() == Direction.DEBIT) {
          debit += e.getAmount().minor();
        } else {
          credit += e.getAmount().minor();
        }
      }
    }
    assertEquals(5000, debit, "DEBIT payout_clearing");
    assertEquals(5000, credit, "CREDIT provider_clearing (balanced, INV-6)");
    assertEquals(WithdrawalStatus.PAID, payouts.findWithdrawal(new WithdrawalId("w1")).get().getStatus());
    assertEquals(1, payouts.allTxns().size());
    assertEquals("EXECUTE_PAYOUT", audit.all().get(0).getAction());
    assertEquals(ADMIN, audit.all().get(0).getActor());
  }

  @Test
  void single_send_blocks_on_unverified_kyc_with_mapped_error() {
    AccountId creator = new AccountId("c2");
    // KYC not verified.
    seedWithdrawal("w2", creator, 5000);
    assertThrows(
        KycBlockedException.class,
        () -> service.send(ADMIN, new WithdrawalId("w2"), new IdempotencyKey("run2")));
    // No disbursement, no txn.
    assertEquals(0, payouts.allTxns().size());
  }

  @Test
  void weekly_run_pays_verified_and_skips_unverified_no_partial_double() {
    AccountId verified = new AccountId("cv");
    AccountId unverified = new AccountId("cu");
    kyc.verify(verified);
    seedWithdrawal("wv", verified, 4000);
    seedWithdrawal("wu", unverified, 7000);

    PayoutBatchView batch = service.runWeekly(ADMIN, new IdempotencyKey("weekly1"));

    // Only the verified creator paid.
    assertEquals(1, batch.count());
    assertEquals(1, payouts.allTxns().size());
    assertEquals(
        WithdrawalStatus.PAID, payouts.findWithdrawal(new WithdrawalId("wv")).get().getStatus());
    // Unverified left pending (skipped, not paid).
    assertEquals(
        WithdrawalStatus.PENDING, payouts.findWithdrawal(new WithdrawalId("wu")).get().getStatus());
  }

  @Test
  void rerun_weekly_does_not_double_pay_an_already_paid_withdrawal() {
    AccountId creator = new AccountId("c3");
    kyc.verify(creator);
    seedWithdrawal("w3", creator, 6000);

    service.runWeekly(ADMIN, new IdempotencyKey("weekly-a"));
    int txnsAfterFirst = payouts.allTxns().size();
    long entriesAfterFirst = ledger.entries.stream().filter(e -> "payout".equals(e.getRefType())).count();

    // A second run finds no payable withdrawal (already PAID), so nothing new is posted...
    service.runWeekly(ADMIN, new IdempotencyKey("weekly-b"));
    assertEquals(txnsAfterFirst, payouts.allTxns().size(), "no second payout txn (INV-6)");
    assertEquals(
        entriesAfterFirst,
        ledger.entries.stream().filter(e -> "payout".equals(e.getRefType())).count(),
        "no second disbursement posting (no double-debit, INV-6)");
  }

  @Test
  void durable_guard_blocks_second_disbursement_even_with_stale_payable_status() {
    // Prove it is the DURABLE exactly-once guard (ledger_posting header / uq_payout_per_withdrawal),
    // not merely the in-memory status check, that prevents a double debit: re-set the withdrawal to a
    // stale PAYABLE status (simulating a lost status write on a crashed retry) and run a single send
    // again. The disbursement must NOT post a second time (WU-PAY-3 finding F1 analog).
    AccountId creator = new AccountId("c4");
    kyc.verify(creator);
    WithdrawalRequest w = seedWithdrawal("w4", creator, 8000);
    service.send(ADMIN, new WithdrawalId("w4"), new IdempotencyKey("s1"));

    WithdrawalRequest stale =
        WithdrawalRequest.reconstitute(
            w.getId(), creator, w.getAmount(), w.getFee(), w.getMethodId(),
            WithdrawalStatus.PENDING, w.getReserveTxnId(), w.getIdempotencyKey(),
            w.getRequestedAt());
    payouts.saveWithdrawal(stale);

    long payoutEntriesBefore =
        ledger.entries.stream().filter(e -> "payout".equals(e.getRefType())).count();
    // A second single send now trips the exactly-once guard → the withdrawal is treated as already
    // paid and the send surfaces a conflict rather than double-debiting.
    assertThrows(
        RuntimeException.class,
        () -> service.send(ADMIN, new WithdrawalId("w4"), new IdempotencyKey("s2")));
    assertEquals(
        payoutEntriesBefore,
        ledger.entries.stream().filter(e -> "payout".equals(e.getRefType())).count(),
        "exactly-once guard blocks the second disbursement (INV-6)");
    assertEquals(1, payouts.allTxns().size(), "still exactly one payout txn");
  }
}
