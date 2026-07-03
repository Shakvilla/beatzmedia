package org.shakvilla.beatzmedia.payments.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.service.TipLedgerPoster;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.PaymentSettled;
import org.shakvilla.beatzmedia.payments.domain.TipRef;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Adversarial concurrency test for finding F1 — a concurrent double-settle must NOT double-credit a
 * creator (INV-1/INV-6). Two settlement postings for the SAME intent are driven <em>concurrently</em>
 * (two threads), simulating two provider webhooks with different {@code provider_event_id} that both
 * pass the {@code payment_event} UNIQUE check and both fire {@code PaymentSettled}. Their tip-split
 * postings run in sibling {@code REQUIRES_NEW} transactions and race on the ledger.
 *
 * <p><strong>This test FAILS against the pre-fix code</strong> (check-then-act {@code existsPostingFor}
 * with no backing constraint → both siblings see "no posting" and both post → creator credited twice,
 * {@code available == 1800}). It <strong>PASSES after the fix</strong>: the {@code ledger_posting}
 * UNIQUE header makes the losing poster fail on INSERT and roll back, so exactly one posting lands and
 * the creator is credited once ({@code available == 900}).
 */
@QuarkusTest
@Tag("integration")
class ConcurrentSettlementIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String TIP_URL = "/v1/payments/tips";

  private static final String FAN_EMAIL = "concurrent-tip-fan@example.com";
  private static final String FAN_PASSWORD = "password123";

  @Inject EntityManager em;
  @Inject TipLedgerPoster poster;

  private String fanToken;

  @BeforeEach
  void setUp() {
    given()
        .contentType(ContentType.JSON)
        .body(
            "{ \"name\": \"Concurrent Fan\", \"email\": \"%s\", \"password\": \"%s\" }"
                .formatted(FAN_EMAIL, FAN_PASSWORD))
        .when()
        .post(SIGNUP_URL);
    fanToken =
        given()
            .contentType(ContentType.JSON)
            .body("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(FAN_EMAIL, FAN_PASSWORD))
            .when()
            .post(LOGIN_URL)
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("token");
  }

  @Test
  void concurrent_double_settle_credits_creator_exactly_once() throws Exception {
    String creator = "cc-creator-" + System.nanoTime();
    String intentId = issueTip("cc-key-" + System.nanoTime(), creator, 10.00);

    // Build TWO PaymentSettled events for the SAME intent (as two racing webhook deliveries would):
    // identical intent + tip order-ref + amount, differing only in the (irrelevant-to-posting) event
    // instance. Both target the same creator payable via TipRef.
    String orderRef = TipRef.forCreator(new AccountId(creator)).value();
    PaymentSettled a =
        new PaymentSettled(
            intentId, orderRef, "fan", 1000, "GHS", "mtn", "pref-" + intentId, java.time.Instant.now());
    PaymentSettled b =
        new PaymentSettled(
            intentId, orderRef, "fan", 1000, "GHS", "mtn", "pref-" + intentId, java.time.Instant.now());

    // Fire both postings concurrently, hard against the ledger claim (this is the F1 race window).
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger duplicates = new AtomicInteger();

    Runnable post =
        () -> {
          try {
            start.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
          try {
            poster.postTip(pickFor(Thread.currentThread(), a, b));
            successes.incrementAndGet();
          } catch (org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePostingException e) {
            duplicates.incrementAndGet();
          }
        };

    pool.submit(post);
    pool.submit(post);
    start.countDown(); // release both threads simultaneously
    pool.shutdown();
    assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "postings did not finish in time");

    // Exactly one posting won; the other lost the exactly-once claim (or both serialised to one credit).
    assertEquals(
        900,
        availableFor(creator),
        "creator credited exactly once (₵10 tip @ 10% = 900), never double-credited (F1/INV-1)");
    assertEquals(1, postingRows("tip", intentId), "exactly one ledger_posting header row for the intent");
    assertEquals(
        1, creatorPayableCreditRows(creator), "exactly one creator_payable CREDIT row for the tip");
    assertTrue(successes.get() >= 1, "at least one poster must succeed");
    assertEquals(2, successes.get() + duplicates.get(), "both threads accounted for");
  }

  private static PaymentSettled pickFor(Thread t, PaymentSettled a, PaymentSettled b) {
    return (t.threadId() % 2 == 0) ? a : b;
  }

  // ---- helpers ----------------------------------------------------------

  private String issueTip(String idempotencyKey, String creatorId, double amount) {
    return given()
        .header("Authorization", "Bearer " + fanToken)
        .header("Idempotency-Key", idempotencyKey)
        .contentType(ContentType.JSON)
        .body(
            """
            {
              "creatorId": "%s",
              "amount": { "amount": %s, "currency": "GHS" },
              "provider": "mtn",
              "methodKind": "momo",
              "paymentToken": "tok-tip"
            }
            """
                .formatted(creatorId, amount))
        .when()
        .post(TIP_URL)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getString("intentId");
  }

  @Transactional
  long availableFor(String creatorId) {
    Object v =
        em.createNativeQuery("SELECT available_minor FROM creator_balance WHERE account_id = :id")
            .setParameter("id", creatorId)
            .getResultList()
            .stream()
            .findFirst()
            .orElse(0L);
    return ((Number) v).longValue();
  }

  @Transactional
  long postingRows(String refType, String refId) {
    Object v =
        em.createNativeQuery(
                "SELECT COUNT(*) FROM ledger_posting WHERE ref_type = :t AND ref_id = :r")
            .setParameter("t", refType)
            .setParameter("r", refId)
            .getSingleResult();
    return ((Number) v).longValue();
  }

  @Transactional
  long creatorPayableCreditRows(String creatorId) {
    Object v =
        em.createNativeQuery(
                "SELECT COUNT(*) FROM ledger_entry e JOIN ledger_account a ON a.id = e.account_id "
                    + "WHERE a.kind = 'creator_payable' AND a.owner_account_id = :id "
                    + "AND e.direction = 'CREDIT'")
            .setParameter("id", creatorId)
            .getSingleResult();
    return ((Number) v).longValue();
  }
}
