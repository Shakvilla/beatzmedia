package org.shakvilla.beatzmedia.payments.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.in.AddPayoutMethod;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutMethodView;
import org.shakvilla.beatzmedia.payments.application.port.in.RequestWithdrawal;
import org.shakvilla.beatzmedia.payments.application.port.in.RunWeeklyPayouts;
import org.shakvilla.beatzmedia.payments.application.port.in.SendSinglePayout;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.Direction;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountId;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountKind;
import org.shakvilla.beatzmedia.payments.domain.LedgerEntry;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Concurrency integration test for admin payout runs (finding F1). Proves that the per-withdrawal
 * {@code REQUIRES_NEW} boundary + {@code FOR UPDATE SKIP LOCKED} make weekly runs BOTH correct and
 * resilient under contention:
 *
 * <ul>
 *   <li>two truly-simultaneous {@code runWeekly} over the SAME set of payable withdrawals pay each
 *       withdrawal <strong>exactly once</strong> (no double-pay, INV-6) and, between them, pay
 *       <strong>every</strong> withdrawal (no withdrawal lost to a poisoned batch);
 *   <li>a {@code runWeekly} racing a single {@code send} on one withdrawal produces exactly one
 *       payout for it, and the batch still pays the other creators.
 * </ul>
 *
 * <p>Before the fix, a duplicate-claim 23505 inside the single batch transaction poisoned the whole
 * run, un-paying every other creator it had already processed.
 */
@QuarkusTest
@Tag("integration")
class ConcurrentPayoutRunIT {

  @Inject RequestWithdrawal requestWithdrawal;
  @Inject RunWeeklyPayouts runWeeklyPayouts;
  @Inject SendSinglePayout sendSinglePayout;
  @Inject AddPayoutMethod addPayoutMethod;
  @Inject LedgerRepository ledger;
  @Inject IdGenerator ids;
  @Inject AgroalDataSource dataSource;

  private static Money ghs(long minor) {
    return Money.ofMinor(minor, Currency.GHS);
  }

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
  void setKycVerified(AccountId creator) {
    var em =
        io.quarkus.arc.Arc.container()
            .instance(jakarta.persistence.EntityManager.class)
            .get();
    em.createNativeQuery(
            "INSERT INTO kyc_record (account_id, status, verified_at, updated_at) "
                + "VALUES (:a, 'verified', now(), now()) "
                + "ON CONFLICT (account_id) DO UPDATE SET status='verified'")
        .setParameter("a", creator.value())
        .executeUpdate();
  }

  /** Seed a KYC-verified creator + momo method + a payable ₵50 withdrawal; return the withdrawal id. */
  private WithdrawalId seedPayableWithdrawal(String tag) {
    AccountId creator = new AccountId("crun-" + tag + "-" + System.nanoTime());
    seedBalance(creator, 20_000);
    setKycVerified(creator);
    PayoutMethodView m =
        addPayoutMethod.add(
            creator, new AddPayoutMethod.Command("MTN", "024...", MethodKind.momo));
    var view =
        requestWithdrawal.request(
            creator,
            new RequestWithdrawal.Command(ghs(5_000), new PayoutMethodId(m.id())),
            new IdempotencyKey("crun-wd-" + System.nanoTime()));
    return new WithdrawalId(view.id());
  }

  @Test
  void two_concurrent_weekly_runs_pay_each_withdrawal_once_and_none_is_lost() throws Exception {
    List<WithdrawalId> ids0 = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      ids0.add(seedPayableWithdrawal("w" + i));
    }

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    Callable<Throwable> run =
        () -> {
          try {
            barrier.await(5, TimeUnit.SECONDS);
            runWeeklyPayouts.runWeekly(
                "admin-crun", new IdempotencyKey("crun-weekly-" + System.nanoTime()));
            return null;
          } catch (Throwable t) {
            return t;
          }
        };
    Future<Throwable> f1 = pool.submit(run);
    Future<Throwable> f2 = pool.submit(run);
    Throwable e1 = f1.get(30, TimeUnit.SECONDS);
    Throwable e2 = f2.get(30, TimeUnit.SECONDS);
    pool.shutdownNow();

    // Neither run may blow up (in particular never a poisoned-transaction rollback that surfaces as
    // a 500 / rolled-back batch).
    assertTrue(e1 == null, () -> "run 1 must not error: " + e1);
    assertTrue(e2 == null, () -> "run 2 must not error: " + e2);

    // Each withdrawal paid EXACTLY once (no double-pay) and its disbursement is balanced (INV-6).
    for (WithdrawalId id : ids0) {
      assertEquals(1, countPayoutTxns(id.value()), "exactly one payout_txn for " + id);
      assertEquals(2, countPayoutEntries(id.value()), "one balanced disbursement (2 rows) for " + id);
      assertTrue(ledgerBalancedForRef("payout", id.value()), "balanced disburse for " + id);
      assertEquals("paid", statusOf(id.value()), "withdrawal marked paid: " + id);
    }
    // Every withdrawal was covered between the two runs (none lost to a poisoned batch).
    assertEquals(ids0.size(), ids0.stream().filter(id -> countPayoutTxns(id.value()) == 1).count());
  }

  @Test
  void weekly_run_racing_a_single_send_pays_the_shared_withdrawal_once() throws Exception {
    WithdrawalId shared = seedPayableWithdrawal("shared");
    List<WithdrawalId> others = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      others.add(seedPayableWithdrawal("other" + i));
    }

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);

    Callable<Throwable> weekly =
        () -> {
          try {
            barrier.await(5, TimeUnit.SECONDS);
            runWeeklyPayouts.runWeekly(
                "admin-crun", new IdempotencyKey("crun-weekly2-" + System.nanoTime()));
            return null;
          } catch (Throwable t) {
            return t;
          }
        };
    Callable<Throwable> single =
        () -> {
          try {
            barrier.await(5, TimeUnit.SECONDS);
            sendSinglePayout.send(
                "admin-crun", shared, new IdempotencyKey("crun-send-" + System.nanoTime()));
            return null;
          } catch (Throwable t) {
            // The single send MAY lose the race (withdrawal already paid by the weekly run) and throw
            // an already-paid conflict — that is acceptable; what matters is exactly one payment.
            return t;
          }
        };
    Future<Throwable> fw = pool.submit(weekly);
    Future<Throwable> fs = pool.submit(single);
    fw.get(30, TimeUnit.SECONDS);
    fs.get(30, TimeUnit.SECONDS);
    pool.shutdownNow();

    // The shared withdrawal is paid EXACTLY once regardless of who won.
    assertEquals(1, countPayoutTxns(shared.value()), "shared withdrawal paid exactly once");
    assertEquals(2, countPayoutEntries(shared.value()), "one balanced disbursement for shared");
    // The weekly run still paid the other creators (batch not poisoned by the race).
    for (WithdrawalId id : others) {
      assertEquals(1, countPayoutTxns(id.value()), "other withdrawal still paid: " + id);
    }
  }

  // ---- helpers ----------------------------------------------------------

  private boolean ledgerBalancedForRef(String refType, String refId) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT COALESCE(SUM(CASE WHEN direction='DEBIT' THEN amount_minor "
                    + "ELSE -amount_minor END),0) FROM ledger_entry WHERE ref_type=? AND ref_id=?")) {
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
    return countQuery(
        "SELECT count(*) FROM ledger_entry WHERE ref_type='payout' AND ref_id = ?", withdrawalId);
  }

  private int countPayoutTxns(String withdrawalId) {
    return countQuery("SELECT count(*) FROM payout_txn WHERE withdrawal_id = ?", withdrawalId);
  }

  private String statusOf(String withdrawalId) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement("SELECT status FROM withdrawal_request WHERE id = ?")) {
      ps.setString(1, withdrawalId);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getString(1);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private int countQuery(String sql, String param) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, param);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
