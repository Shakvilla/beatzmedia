package org.shakvilla.beatzmedia.payments.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
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
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Concurrency integration test for {@link RequestWithdrawal} — the double-spend guard (INV-8). This is
 * the class of bug the WU brief calls out (WU-PAY-3 double-settle / WU-COM-2 multi-creator collision):
 * two truly-simultaneous withdrawals against the SAME creator balance, each ≤ available on its own but
 * summing to MORE than available, must NOT both succeed and overdraw the balance.
 *
 * <p>Runs against real Postgres so the {@code creator_balance} row lock ({@code SELECT ... FOR UPDATE})
 * actually serialises the two: exactly ONE succeeds, the loser fails with a mapped {@code
 * INSUFFICIENT_BALANCE}, and the creator's available balance never goes negative and reflects exactly
 * one reservation.
 */
@QuarkusTest
@Tag("integration")
class ConcurrentWithdrawalIT {

  @Inject RequestWithdrawal requestWithdrawal;
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

  @Test
  void two_concurrent_withdrawals_cannot_overdraw_the_same_balance() throws Exception {
    AccountId creator = new AccountId("conc-wd-" + System.nanoTime());
    seedBalance(creator, 3_000); // ₵30 available
    setKycVerified(creator);
    PayoutMethodView m =
        addPayoutMethod.add(
            creator,
            new AddPayoutMethod.Command(
                "MTN", "024...", MethodKind.momo, "mtn", "0244000000", null, null, null, null));
    PayoutMethodId method = new PayoutMethodId(m.id());

    // Each asks ₵20 (≤ ₵30 alone), but ₵40 together > ₵30 → exactly one may win.
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);

    Callable<Outcome> task =
        () -> {
          try {
            barrier.await(5, TimeUnit.SECONDS);
            var view =
                requestWithdrawal.request(
                    creator,
                    new RequestWithdrawal.Command(ghs(2_000), method),
                    new IdempotencyKey("cwd-" + System.nanoTime() + "-" + Thread.currentThread().getId()));
            return new Outcome(view.id(), null);
          } catch (Throwable t) {
            return new Outcome(null, t);
          }
        };

    Future<Outcome> f1 = pool.submit(task);
    Future<Outcome> f2 = pool.submit(task);
    Outcome o1 = f1.get(20, TimeUnit.SECONDS);
    Outcome o2 = f2.get(20, TimeUnit.SECONDS);
    pool.shutdownNow();

    long successes = List.of(o1, o2).stream().filter(o -> o.withdrawalId != null).count();
    long insufficient =
        List.of(o1, o2).stream()
            .filter(
                o ->
                    o.error
                        instanceof
                        org.shakvilla.beatzmedia.payments.domain.InsufficientBalanceException)
            .count();

    assertEquals(1, successes, "exactly one withdrawal may win the ₵30 balance");
    assertEquals(1, insufficient, "the loser must fail with a mapped INSUFFICIENT_BALANCE");

    // The balance reflects exactly ONE ₵20 reservation and is never negative.
    long available = availableFor(creator);
    assertEquals(1_000, available, "available reflects exactly one reservation (₵30 − ₵20)");
    assertTrue(available >= 0, "balance must never go negative (INV-8)");
    assertEquals(1, countReserveTxns(creator), "exactly one reserve posting committed");
  }

  private long availableFor(AccountId creator) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT available_minor FROM creator_balance WHERE account_id = ?")) {
      ps.setString(1, creator.value());
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private int countReserveTxns(AccountId creator) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT count(*) FROM ledger_entry e JOIN ledger_account a ON a.id = e.account_id "
                    + "WHERE e.ref_type='withdraw' AND e.direction='DEBIT' "
                    + "AND a.kind='creator_payable' AND a.owner_account_id = ?")) {
      ps.setString(1, creator.value());
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private record Outcome(String withdrawalId, Throwable error) {}
}
