package org.shakvilla.beatzmedia.payments.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.in.AddPayoutMethod;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutBatchView;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutMethodView;
import org.shakvilla.beatzmedia.payments.application.port.in.RequestWithdrawal;
import org.shakvilla.beatzmedia.payments.application.port.in.RunWeeklyPayouts;
import org.shakvilla.beatzmedia.payments.application.port.in.WithdrawalView;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.BelowMinPayoutException;
import org.shakvilla.beatzmedia.payments.domain.Direction;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.InsufficientBalanceException;
import org.shakvilla.beatzmedia.payments.domain.KycRequiredException;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountId;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountKind;
import org.shakvilla.beatzmedia.payments.domain.LedgerEntry;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end integration tests for WU-PAY-4 payouts against real Postgres (Testcontainers via Dev
 * Services). Translates LLFR-PAYMENTS-03.2/03.3 into DB-backed proofs of the money invariants:
 *
 * <ul>
 *   <li><strong>KYC gate (INV-8):</strong> unverified creator cannot withdraw (mapped error).
 *   <li><strong>Balance-backed (INV-8):</strong> below-min and over-available are rejected; a valid
 *       withdrawal reserves a balanced txn that reduces available and leaves the whole ledger
 *       balanced (INV-6).
 *   <li><strong>Balanced payout (INV-6):</strong> the weekly run posts a balanced disbursement and a
 *       re-run does NOT double-debit (exactly-once).
 * </ul>
 */
@QuarkusTest
@Tag("integration")
class PayoutFlowIT {

  @Inject RequestWithdrawal requestWithdrawal;
  @Inject RunWeeklyPayouts runWeeklyPayouts;
  @Inject AddPayoutMethod addPayoutMethod;
  @Inject LedgerRepository ledger;
  @Inject IdGenerator ids;
  @Inject AgroalDataSource dataSource;

  private static Money ghs(long minor) {
    return Money.ofMinor(minor, Currency.GHS);
  }

  private AccountId newCreator() {
    return new AccountId("payout-creator-" + System.nanoTime());
  }

  /** Seed a creator's available balance by posting a cleared sale credit directly via the ledger. */
  @Transactional
  void seedBalance(AccountId creator, long minor) {
    LedgerAccountId providerClearing =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.PROVIDER_CLEARING, "mtn"));
    LedgerAccountId creatorPayable =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.CREATOR_PAYABLE, creator.value()));
    TxnId txn = new TxnId(ids.newId());
    Instant now = Instant.now();
    ledger.postBalanced(
        txn,
        List.of(
            LedgerEntry.post(
                ids.newId(), txn, providerClearing, Direction.DEBIT, ghs(minor),
                "intent", "seed-" + creator.value(), now, now),
            LedgerEntry.post(
                ids.newId(), txn, creatorPayable, Direction.CREDIT, ghs(minor),
                "intent", "seed-" + creator.value(), now, now)));
  }

  @Transactional
  void setKyc(AccountId creator, String status) {
    var em =
        io.quarkus.arc.Arc.container()
            .instance(jakarta.persistence.EntityManager.class)
            .get();
    em.createNativeQuery(
            "INSERT INTO kyc_record (account_id, status, verified_at, updated_at) "
                + "VALUES (:a, :s, now(), now()) "
                + "ON CONFLICT (account_id) DO UPDATE SET status = EXCLUDED.status")
        .setParameter("a", creator.value())
        .setParameter("s", status)
        .executeUpdate();
  }

  private PayoutMethodId addMomo(AccountId creator) {
    PayoutMethodView v =
        addPayoutMethod.add(
            creator, new AddPayoutMethod.Command("MTN MoMo", "024...9210", MethodKind.momo));
    return new PayoutMethodId(v.id());
  }

  // ---- LLFR-PAYMENTS-03.2: KYC + balance-backed withdrawal --------------

  @Test
  void withdrawal_rejected_when_not_kyc_verified() {
    AccountId creator = newCreator();
    seedBalance(creator, 50_000);
    PayoutMethodId method = addMomo(creator);
    assertThrows(
        KycRequiredException.class,
        () ->
            requestWithdrawal.request(
                creator,
                new RequestWithdrawal.Command(ghs(20_000), method),
                new IdempotencyKey("wd-" + System.nanoTime())));
  }

  @Test
  void withdrawal_rejected_below_min_and_over_available() {
    AccountId creator = newCreator();
    seedBalance(creator, 2_000); // ₵20 available
    setKyc(creator, "verified");
    PayoutMethodId method = addMomo(creator);

    assertThrows(
        BelowMinPayoutException.class,
        () ->
            requestWithdrawal.request(
                creator,
                new RequestWithdrawal.Command(ghs(500), method),
                new IdempotencyKey("wd-lo-" + System.nanoTime())));
    assertThrows(
        InsufficientBalanceException.class,
        () ->
            requestWithdrawal.request(
                creator,
                new RequestWithdrawal.Command(ghs(3_000), method),
                new IdempotencyKey("wd-hi-" + System.nanoTime())));
  }

  @Test
  void valid_withdrawal_reserves_balanced_and_reduces_available() {
    AccountId creator = newCreator();
    seedBalance(creator, 10_000); // ₵100
    setKyc(creator, "verified");
    PayoutMethodId method = addMomo(creator);

    WithdrawalView view =
        requestWithdrawal.request(
            creator,
            new RequestWithdrawal.Command(ghs(4_000), method),
            new IdempotencyKey("wd-ok-" + System.nanoTime()));
    assertEquals("pending", view.status());

    // Available reduced by the reservation; whole ledger for this creator still balanced (INV-6).
    assertEquals(6_000, ledger.balanceOf(creator).availableMinor());
    assertTrue(ledgerBalancedForRef("withdraw", view.id()), "reserve txn is balanced (INV-6)");
  }

  // ---- LLFR-PAYMENTS-03.3: balanced payout + no double-pay on re-run ----

  @Test
  void weekly_run_pays_balanced_and_rerun_does_not_double_debit() throws Exception {
    AccountId creator = newCreator();
    seedBalance(creator, 20_000);
    setKyc(creator, "verified");
    PayoutMethodId method = addMomo(creator);

    WithdrawalView w =
        requestWithdrawal.request(
            creator,
            new RequestWithdrawal.Command(ghs(8_000), method),
            new IdempotencyKey("wd-run-" + System.nanoTime()));

    PayoutBatchView batch =
        runWeeklyPayouts.runWeekly("admin-it", new IdempotencyKey("weekly-" + System.nanoTime()));
    // The run pays every payable withdrawal (possibly incl. leftovers from other test methods); the
    // exactly-once assertions below are scoped to THIS withdrawal id.
    assertTrue(batch.count() >= 1, "the weekly run pays at least this withdrawal");

    int payoutRowsAfterFirst = countPayoutEntries(w.id());
    assertEquals(2, payoutRowsAfterFirst, "one balanced disbursement (2 rows) per withdrawal");
    assertTrue(ledgerBalancedForRef("payout", w.id()), "disburse txn is balanced (INV-6)");

    // Re-run: the withdrawal is already PAID (filtered out), so NO second disbursement is posted.
    runWeeklyPayouts.runWeekly("admin-it", new IdempotencyKey("weekly2-" + System.nanoTime()));
    assertEquals(
        payoutRowsAfterFirst,
        countPayoutEntries(w.id()),
        "a re-run must NOT post a second disbursement (no double-debit, INV-6)");
    assertEquals(1, countPayoutTxns(w.id()), "exactly one payout_txn per withdrawal (uq guard)");
  }

  // ---- helpers ----------------------------------------------------------

  private boolean ledgerBalancedForRef(String refType, String refId) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT COALESCE(SUM(CASE WHEN direction='DEBIT' THEN amount_minor "
                    + "ELSE -amount_minor END),0) FROM ledger_entry "
                    + "WHERE ref_type = ? AND ref_id = ?")) {
      ps.setString(1, refType);
      ps.setString(2, refId);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong(1) == 0L;
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private int countPayoutEntries(String withdrawalId) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT count(*) FROM ledger_entry WHERE ref_type='payout' AND ref_id = ?")) {
      ps.setString(1, withdrawalId);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private int countPayoutTxns(String withdrawalId) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement("SELECT count(*) FROM payout_txn WHERE withdrawal_id = ?")) {
      ps.setString(1, withdrawalId);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
