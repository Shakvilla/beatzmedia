package org.shakvilla.beatzmedia.payments.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.in.InitiateCharge;
import org.shakvilla.beatzmedia.payments.application.port.in.PaymentIntentView;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Concurrency integration test for {@link InitiateCharge} (code review BLOCKER 2). Two
 * truly-simultaneous requests carrying the SAME {@code Idempotency-Key} + same body must produce:
 *
 * <ul>
 *   <li>exactly ONE provider charge ({@link CountingPaymentGateway#initiateCalls()} == 1),
 *   <li>exactly ONE persisted {@code payment_intent} row for that key,
 *   <li>the same intent id returned to both callers,
 *   <li>and NEVER a 500 / raw unique-violation (the transaction-scoped advisory lock serialises the
 *       two so the loser returns the winner's intent).
 * </ul>
 *
 * Runs against Testcontainers Postgres so the real {@code pg_advisory_xact_lock} serialisation is
 * exercised. Uses the counting gateway (a CDI alternative) so the provider-charge count is exact.
 */
@QuarkusTest
@Tag("integration")
class InitiateChargeConcurrencyIT {

  @Inject
  InitiateCharge initiateCharge;

  @Inject
  AgroalDataSource dataSource;

  private static final AccountId ACCOUNT = new AccountId("acct-concurrency");
  private static final OrderRef ORDER = new OrderRef("BZ-2026-C0001");
  private static final Money AMOUNT = Money.ofMinor(1500, Currency.GHS);
  private static final PaymentMethodRef METHOD =
      new PaymentMethodRef(Provider.mtn, MethodKind.momo, "tok-conc");

  @BeforeEach
  void resetCounter() {
    CountingPaymentGateway.reset();
  }

  @Test
  void two_simultaneous_same_key_requests_charge_once_and_persist_one_intent() throws Exception {
    String key = "conc-key-" + System.nanoTime();
    IdempotencyKey idem = new IdempotencyKey(key);

    // Barrier releases both threads at the same instant to maximise the race window.
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);

    Callable<Outcome> task =
        () -> {
          try {
            barrier.await(5, TimeUnit.SECONDS);
            PaymentIntentView view = initiateCharge.charge(ACCOUNT, ORDER, AMOUNT, METHOD, idem);
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

    // Neither thread may fail — in particular never a 500 / unique-violation.
    assertNull(o1.error, () -> "thread 1 must not error: " + stringify(o1.error));
    assertNull(o2.error, () -> "thread 2 must not error: " + stringify(o2.error));

    assertNotNull(o1.intentId);
    assertNotNull(o2.intentId);
    assertEquals(o1.intentId, o2.intentId, "both callers must receive the same intent");

    assertEquals(
        1,
        CountingPaymentGateway.initiateCalls(),
        "exactly one provider charge for a single idempotency key");

    assertEquals(1, countIntentsForKey(key), "exactly one persisted payment_intent row for the key");
  }

  private int countIntentsForKey(String key) throws Exception {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement("SELECT count(*) FROM payment_intent WHERE idempotency_key = ?")) {
      ps.setString(1, key);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    }
  }

  private static String stringify(Throwable t) {
    return t == null ? "<none>" : t.getClass().getName() + ": " + t.getMessage();
  }

  private record Outcome(String intentId, Throwable error) {}
}
