package org.shakvilla.beatzmedia.payments.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.SandboxPaymentGateway;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * End-to-end integration for WU-PAY-5 refunds / chargebacks / disputes + ownership revocation +
 * clawback (LLFR-PAYMENTS-04.1 .. 04.3, INV-9). Testcontainers Postgres + REST-assured. The full path
 * is exercised: cart → checkout → provider webhook settlement → ownership grant + 70/30 split → open a
 * dispute → admin refund (or provider chargeback LOST) → ownership REVOKED (grant no longer active) +
 * creator credit CLAWED BACK (balanced ledger reversal, INV-6) → dispute {@code refunded}.
 *
 * <p>Proves the money invariants reviewers check:
 *
 * <ul>
 *   <li><strong>INV-9</strong> — a refund revokes the grants AND claws back the creator's credit.
 *   <li><strong>Album refund revokes ALL constituent tracks</strong> (mirrors INV-2 expansion).
 *   <li><strong>Already-withdrawn → NEGATIVE balance</strong> (owed) — modelled, not skipped.
 *   <li><strong>Chargeback LOST</strong> (via the signature-verified webhook) forces refund + revoke.
 *   <li><strong>Exactly-once / concurrency</strong> — a re-delivered refund/chargeback event, and two
 *       concurrent refunds of the same order, never double-revoke or double-clawback.
 * </ul>
 */
@QuarkusTest
@Tag("integration")
class RefundDisputeIT {

  private static final String PASSWORD = "password123";
  private static final String WEBHOOK_URL = "/v1/payments/webhooks/mtn";

  @Inject EntityManager em;

  @ConfigProperty(name = "beatz.payment.webhook-secret")
  String webhookSecret;

  // ---- LLFR-PAYMENTS-04.2 : admin refund revokes ownership + claws back credit ----

  @Test
  void adminRefund_revokesOwnership_andClawsBackCreatorCredit_INV9() {
    long n = System.nanoTime();
    String artist = "rf-artist-" + n;
    String track = "rf-track-" + n;
    seedArtist(artist, "RF Artist");
    seedTrack(track, "RF Track", 500, null, artist, "RF Artist");

    String buyerEmail = "rf-buyer-" + n + "@example.com";
    String buyerToken = signUp(buyerEmail);
    String account = accountIdOf(buyerEmail);

    // Buy + settle → owns the track, creator credited 70% of 500 = 350.
    String reference = buyAndSettle(buyerToken, "track", track);
    assertEquals(1, activeGrantCount(account, track), "owns after settlement (INV-1)");
    assertEquals(350, availableFor(artist), "creator credited 70% (INV-4)");

    String intentId = intentOfOrder(reference);
    String disputeId = openDispute(reference, intentId, "Refund request", "@buyer", 500, false, null);

    String financeToken = financeToken(n);

    // Admin refunds the dispute.
    given()
        .header("Authorization", "Bearer " + financeToken)
        .header("Idempotency-Key", "rf-refund-" + n)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"not delivered\"}")
        .when().post("/v1/admin/finance/disputes/" + disputeId + "/refund")
        .then().statusCode(200)
        .body("status", equalTo("refunded"));

    // INV-9: the grant is now revoked (preview-gated again) and the credit is clawed back to 0.
    assertEquals(0, activeGrantCount(account, track), "ownership REVOKED after refund (INV-9)");
    assertEquals(0, availableFor(artist), "creator credit CLAWED BACK (INV-9)");
    assertEquals("refunded", orderStatus(reference), "order marked refunded");
    // The clawback reversal is balanced (INV-6) and the whole ledger nets to zero.
    assertEquals(0, ledgerImbalance(), "ledger stays balanced after clawback (INV-6)");
    assertEquals(1, refundCount(disputeId), "exactly one refund row");
  }

  // ---- album refund revokes ALL constituent track grants (mirrors INV-2) ----------

  @Test
  void albumRefund_revokesAllConstituentTrackGrants() {
    long n = System.nanoTime();
    String artist = "rf-alb-artist-" + n;
    String album = "rf-album-" + n;
    String t1 = "rf-albtrack1-" + n;
    String t2 = "rf-albtrack2-" + n;
    seedArtist(artist, "RF Alb Artist");
    seedAlbum(album, "RF Album", artist, "RF Alb Artist", 2000);
    seedTrack(t1, "Album Track 1", 0, album, artist, "RF Alb Artist");
    seedTrack(t2, "Album Track 2", 0, album, artist, "RF Alb Artist");

    String buyerEmail = "rf-albbuyer-" + n + "@example.com";
    String buyerToken = signUp(buyerEmail);
    String account = accountIdOf(buyerEmail);

    String reference = buyAndSettle(buyerToken, "album", album);
    assertEquals(1, activeGrantCount(account, t1), "owns track 1 (INV-2)");
    assertEquals(1, activeGrantCount(account, t2), "owns track 2 (INV-2)");

    String intentId = intentOfOrder(reference);
    String disputeId = openDispute(reference, intentId, "Refund request", "@buyer", 2000, false, null);
    String financeToken = financeToken(n);

    given()
        .header("Authorization", "Bearer " + financeToken)
        .header("Idempotency-Key", "rf-albrefund-" + n)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"album refund\"}")
        .when().post("/v1/admin/finance/disputes/" + disputeId + "/refund")
        .then().statusCode(200);

    // ALL constituent tracks revoked (album refund mirrors the INV-2 expansion).
    assertEquals(0, activeGrantCount(account, t1), "album track 1 grant revoked");
    assertEquals(0, activeGrantCount(account, t2), "album track 2 grant revoked");
    assertEquals(0, ledgerImbalance(), "ledger balanced after album clawback");
  }

  // ---- already-withdrawn → clawback drives balance NEGATIVE (owed) ----------------

  @Test
  void refundAfterCreatorWithdrew_drivesBalanceNegative_owed() {
    long n = System.nanoTime();
    String artist = "rf-neg-artist-" + n;
    String track = "rf-neg-track-" + n;
    seedArtist(artist, "RF Neg Artist");
    // Priced ₵20 so 70% = 1400 ≥ the ₵10 withdrawal floor (INV-8).
    seedTrack(track, "RF Neg Track", 2000, null, artist, "RF Neg Artist");

    String buyerEmail = "rf-negbuyer-" + n + "@example.com";
    String buyerToken = signUp(buyerEmail);
    String reference = buyAndSettle(buyerToken, "track", track);
    assertEquals(1400, availableFor(artist), "creator credited 70% of 2000");

    // Creator withdraws the whole ₵14.00 available (drains creator_payable via the reserve DEBIT).
    withdrawAll(artist, n, 1400);
    assertEquals(0, availableFor(artist), "available drained by the withdrawal");

    String intentId = intentOfOrder(reference);
    String disputeId = openDispute(reference, intentId, "Chargeback", "card", 2000, false, null);
    String financeToken = financeToken(n);

    given()
        .header("Authorization", "Bearer " + financeToken)
        .header("Idempotency-Key", "rf-negrefund-" + n)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"already withdrawn\"}")
        .when().post("/v1/admin/finance/disputes/" + disputeId + "/refund")
        .then().statusCode(200);

    // The creator no longer held the credit — the clawback drives available NEGATIVE (recovery owed),
    // it is modelled explicitly, NOT silently skipped (INV-9 / requirement 2).
    assertEquals(-1400, availableFor(artist), "clawback of withdrawn credit → negative available (owed)");
    assertEquals(0, ledgerImbalance(), "ledger still balanced even when creator goes negative (INV-6)");
  }

  // ---- LLFR-PAYMENTS-04.3 : reject / escalate (no money moves, no revoke) ---------

  @Test
  void rejectAndEscalate_moveNoMoney_andDoNotRevoke() {
    long n = System.nanoTime();
    String artist = "rf-rej-artist-" + n;
    String track = "rf-rej-track-" + n;
    seedArtist(artist, "RF Rej Artist");
    seedTrack(track, "RF Rej Track", 500, null, artist, "RF Rej Artist");
    String buyerEmail = "rf-rejbuyer-" + n + "@example.com";
    String buyerToken = signUp(buyerEmail);
    String account = accountIdOf(buyerEmail);
    String reference = buyAndSettle(buyerToken, "track", track);
    String intentId = intentOfOrder(reference);
    String financeToken = financeToken(n);

    // Reject a dispute → rejected; ownership intact, credit intact.
    String d1 = openDispute(reference, intentId, "Refund request", "@buyer", 500, false, null);
    given()
        .header("Authorization", "Bearer " + financeToken)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"not eligible\"}")
        .when().post("/v1/admin/finance/disputes/" + d1 + "/reject")
        .then().statusCode(200).body("status", equalTo("rejected"));
    assertEquals(1, activeGrantCount(account, track), "reject does NOT revoke ownership");
    assertEquals(350, availableFor(artist), "reject does NOT claw back credit");

    // A second dispute can be escalated → escalated.
    String d2 = openDispute(reference, intentId, "Refund request", "@buyer", 500, false, null);
    given()
        .header("Authorization", "Bearer " + financeToken)
        .when().post("/v1/admin/finance/disputes/" + d2 + "/escalate")
        .then().statusCode(200).body("status", equalTo("escalated"));
    assertEquals(1, activeGrantCount(account, track), "escalate does NOT revoke ownership");
  }

  // ---- LLFR-PAYMENTS-04.1 : get dispute + timeline; RBAC ---------------------------

  @Test
  void getDispute_returnsDetailAndTimeline_financeScoped() {
    long n = System.nanoTime();
    String artist = "rf-get-artist-" + n;
    String track = "rf-get-track-" + n;
    seedArtist(artist, "RF Get Artist");
    seedTrack(track, "RF Get Track", 500, null, artist, "RF Get Artist");
    String buyerEmail = "rf-getbuyer-" + n + "@example.com";
    String buyerToken = signUp(buyerEmail);
    String reference = buyAndSettle(buyerToken, "track", track);
    String intentId = intentOfOrder(reference);
    String disputeId = openDispute(reference, intentId, "Refund request", "@buyer", 500, false, null);
    seedDisputeEvent(disputeId, "Dispute opened by fan");
    String financeToken = financeToken(n);

    given()
        .header("Authorization", "Bearer " + financeToken)
        .when().get("/v1/admin/finance/disputes/" + disputeId)
        .then().statusCode(200)
        .body("id", equalTo(disputeId))
        .body("status", equalTo("open"))
        .body("amount.currency", equalTo("GHS"))
        .body("timeline.size()", equalTo(1));

    // A fan (no finance role) is forbidden.
    given()
        .header("Authorization", "Bearer " + buyerToken)
        .when().get("/v1/admin/finance/disputes/" + disputeId)
        .then().statusCode(403);

    // Unknown id → 404 DISPUTE_NOT_FOUND.
    given()
        .header("Authorization", "Bearer " + financeToken)
        .when().get("/v1/admin/finance/disputes/nope-" + n)
        .then().statusCode(404).body("error.code", equalTo("DISPUTE_NOT_FOUND"));
  }

  // ---- chargeback LOST (via signature-verified webhook) forces refund + revoke ----

  @Test
  void chargebackLost_viaWebhook_forcesRefundClawbackAndRevoke() {
    long n = System.nanoTime();
    String artist = "rf-cb-artist-" + n;
    String track = "rf-cb-track-" + n;
    seedArtist(artist, "RF CB Artist");
    seedTrack(track, "RF CB Track", 500, null, artist, "RF CB Artist");
    String buyerEmail = "rf-cbbuyer-" + n + "@example.com";
    String buyerToken = signUp(buyerEmail);
    String account = accountIdOf(buyerEmail);
    String reference = buyAndSettle(buyerToken, "track", track);
    assertEquals(1, activeGrantCount(account, track));
    assertEquals(350, availableFor(artist));

    String intentId = intentOfOrder(reference);
    String providerRef = providerRefOf(intentId);
    String caseId = "cb-case-" + n;

    // Provider opens a chargeback (signature-verified webhook) → dispute opened, no money moves yet.
    chargebackWebhook(providerRef, "cb-open-" + n, "chargeback", caseId);
    assertEquals(1, activeGrantCount(account, track), "chargeback OPEN does not revoke yet");
    assertEquals(350, availableFor(artist), "chargeback OPEN does not claw back yet");

    // Provider rules the platform LOST → forces refund + clawback + revoke (INV-9).
    chargebackWebhook(providerRef, "cb-lost-" + n, "chargeback_lost", caseId);
    assertEquals(0, activeGrantCount(account, track), "LOST chargeback REVOKES ownership (INV-9)");
    assertEquals(0, availableFor(artist), "LOST chargeback CLAWS BACK the credit (INV-9)");
    assertEquals(0, ledgerImbalance(), "ledger balanced after chargeback clawback");
  }

  // ---- exactly-once: a re-delivered refund does NOT double-clawback / double-revoke

  @Test
  void redeliveredChargebackLost_clawsBackAndRevokesExactlyOnce() {
    long n = System.nanoTime();
    String artist = "rf-red-artist-" + n;
    String track = "rf-red-track-" + n;
    seedArtist(artist, "RF Red Artist");
    seedTrack(track, "RF Red Track", 500, null, artist, "RF Red Artist");
    String buyerEmail = "rf-redbuyer-" + n + "@example.com";
    String buyerToken = signUp(buyerEmail);
    String account = accountIdOf(buyerEmail);
    String reference = buyAndSettle(buyerToken, "track", track);

    String intentId = intentOfOrder(reference);
    String providerRef = providerRefOf(intentId);
    String caseId = "cb-red-case-" + n;

    chargebackWebhook(providerRef, "cb-red-open-" + n, "chargeback", caseId);
    // TWO lost events for the same case, different event ids (re-delivery). The 2nd must be a no-op.
    chargebackWebhook(providerRef, "cb-red-lost-a-" + n, "chargeback_lost", caseId);
    chargebackWebhook(providerRef, "cb-red-lost-b-" + n, "chargeback_lost", caseId);

    assertEquals(0, activeGrantCount(account, track), "still exactly revoked (not double-revoked)");
    // Exactly ONE clawback (one refund reversal, not two): balance clawed back to 0, not to -350.
    assertEquals(0, availableFor(artist), "clawed back exactly once (no double-clawback)");
    assertEquals(1, refundCountForIntent(intentId), "exactly one refund for the intent");
    assertEquals(0, ledgerImbalance(), "ledger balanced");
  }

  // ---- concurrency: two simultaneous refunds of the SAME dispute → one clawback ----

  @Test
  void twoConcurrentRefunds_ofSameDispute_clawBackExactlyOnce() throws Exception {
    long n = System.nanoTime();
    String artist = "rf-con-artist-" + n;
    String track = "rf-con-track-" + n;
    seedArtist(artist, "RF Con Artist");
    seedTrack(track, "RF Con Track", 500, null, artist, "RF Con Artist");
    String buyerEmail = "rf-conbuyer-" + n + "@example.com";
    String buyerToken = signUp(buyerEmail);
    String account = accountIdOf(buyerEmail);
    String reference = buyAndSettle(buyerToken, "track", track);
    assertEquals(350, availableFor(artist));

    String intentId = intentOfOrder(reference);
    String disputeId = openDispute(reference, intentId, "Refund request", "@buyer", 500, false, null);
    String financeToken = financeToken(n);

    java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    java.util.concurrent.Callable<Integer> refundOnce =
        () -> {
          barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
          // Distinct idempotency keys so this proves the DURABLE exactly-once (ledger_posting +
          // uq_refund_per_dispute + FOR UPDATE), not just key-based dedup.
          Response r =
              given()
                  .header("Authorization", "Bearer " + financeToken)
                  .header("Idempotency-Key", "rf-con-" + System.nanoTime())
                  .contentType(ContentType.JSON)
                  .body("{\"reason\":\"race\"}")
                  .when().post("/v1/admin/finance/disputes/" + disputeId + "/refund");
          return r.statusCode();
        };
    var f1 = pool.submit(refundOnce);
    var f2 = pool.submit(refundOnce);
    int s1 = f1.get(30, java.util.concurrent.TimeUnit.SECONDS);
    int s2 = f2.get(30, java.util.concurrent.TimeUnit.SECONDS);
    pool.shutdownNow();

    // Neither request 500s; both observe a consistent refunded dispute.
    assertTrue(s1 == 200, "refund 1 status: " + s1);
    assertTrue(s2 == 200, "refund 2 status: " + s2);

    // Exactly ONE clawback despite two concurrent refunds (no double-clawback, no double-revoke).
    assertEquals(1, refundCount(disputeId), "exactly one refund row for the dispute");
    assertEquals(0, activeGrantCount(account, track), "revoked exactly once");
    assertEquals(0, availableFor(artist), "clawed back exactly once (0, not -350)");
    assertEquals(0, ledgerImbalance(), "ledger balanced");
  }

  // ================================ helpers =====================================

  private String buyAndSettle(String token, String kind, String refId) {
    addToCart(token, kind, refId);
    Response co = checkout(token, "rf-key-" + System.nanoTime());
    String reference = co.then().statusCode(202).extract().jsonPath().getString("reference");
    String intentId = co.jsonPath().getString("paymentIntentId");
    settle(intentId, "rf-ev-" + System.nanoTime());
    return reference;
  }

  private void addToCart(String token, String kind, String refId) {
    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("{ \"kind\": \"%s\", \"refId\": \"%s\" }".formatted(kind, refId))
        .when().post("/v1/me/cart/items")
        .then().statusCode(200);
  }

  private Response checkout(String token, String idemKey) {
    return given()
        .header("Authorization", "Bearer " + token)
        .header("Idempotency-Key", idemKey)
        .contentType(ContentType.JSON)
        .body("{ \"paymentMethodId\": \"mtn\" }")
        .when().post("/v1/checkout")
        .then().extract().response();
  }

  private void settle(String intentId, String eventId) {
    String providerRef = providerRefOf(intentId);
    byte[] body =
        ("{\"eventId\":\"" + eventId + "\",\"providerRef\":\"" + providerRef
                + "\",\"status\":\"settled\"}")
            .getBytes(StandardCharsets.UTF_8);
    given()
        .header("X-Beatz-Signature", SandboxPaymentGateway.sign(webhookSecret, body))
        .body(body)
        .when().post(WEBHOOK_URL)
        .then().statusCode(200);
  }

  private void chargebackWebhook(String providerRef, String eventId, String status, String caseId) {
    byte[] body =
        ("{\"eventId\":\"" + eventId + "\",\"providerRef\":\"" + providerRef + "\",\"status\":\""
                + status + "\",\"caseId\":\"" + caseId + "\"}")
            .getBytes(StandardCharsets.UTF_8);
    given()
        .header("X-Beatz-Signature", SandboxPaymentGateway.sign(webhookSecret, body))
        .body(body)
        .when().post(WEBHOOK_URL)
        .then().statusCode(200);
  }

  private String signUp(String email) {
    given()
        .contentType(ContentType.JSON)
        .body("{ \"name\": \"RF Fan\", \"email\": \"%s\", \"password\": \"%s\" }"
            .formatted(email, PASSWORD))
        .when().post("/v1/auth/signup");
    return given()
        .contentType(ContentType.JSON)
        .body("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private String financeToken(long n) {
    String email = "rf-fin-" + n + "@example.com";
    var signup =
        given().contentType(ContentType.JSON)
            .body("{\"name\":\"RF Fin\",\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(email, PASSWORD))
            .when().post("/v1/auth/signup").then().statusCode(201).extract().jsonPath();
    grantFinance(signup.getString("account.id"), String.valueOf(n));
    return given().contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login").then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  @Transactional
  void grantFinance(String accountId, String ts) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, 'finance', now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("memberId", "rf-fin-member-" + ts)
        .setParameter("accountId", accountId)
        .executeUpdate();
  }

  @Transactional
  void seedArtist(String id, String name) {
    em.createNativeQuery(
            "INSERT INTO artist_profile (id, name, image, verified)"
                + " VALUES (:id, :name, 'av.jpg', false) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("name", name)
        .executeUpdate();
  }

  @Transactional
  void seedAlbum(String id, String title, String artistId, String artistName, long priceMinor) {
    em.createNativeQuery(
            "INSERT INTO album (id, title, artist_id, artist_name, year, cover_image, list_price_minor)"
                + " VALUES (:id, :title, :aid, :aname, 2024, 'img.jpg', :price)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("title", title)
        .setParameter("aid", artistId)
        .setParameter("aname", artistName)
        .setParameter("price", priceMinor)
        .executeUpdate();
  }

  @Transactional
  void seedTrack(
      String id, String title, long priceMinor, String album, String aid, String artistName) {
    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, album_id, duration_sec, image,"
                + " ownership, price_minor)"
                + " VALUES (:id, :title, :aid, :aname, :album, 180, 'img.jpg', 'for-sale', :price)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("title", title)
        .setParameter("aid", aid)
        .setParameter("aname", artistName)
        .setParameter("album", album)
        .setParameter("price", priceMinor)
        .executeUpdate();
  }

  /** Open a dispute directly (the admin-adjudicated path — a fan complaint recorded by support). */
  @Transactional
  String openDispute(
      String orderRef,
      String intentId,
      String kind,
      String subject,
      long amountMinor,
      boolean chargeback,
      String caseId) {
    String id = "disp-" + System.nanoTime();
    em.createNativeQuery(
            "INSERT INTO dispute (id, order_ref, payment_intent_id, kind, subject, detail,"
                + " amount_minor, is_chargeback, status, provider_case_id, opened_at)"
                + " VALUES (:id, :ref, :intent, :kind, :subject, '', :amt, :cb, 'open', :case, now())")
        .setParameter("id", id)
        .setParameter("ref", orderRef)
        .setParameter("intent", intentId)
        .setParameter("kind", kind)
        .setParameter("subject", subject)
        .setParameter("amt", amountMinor)
        .setParameter("cb", chargeback)
        .setParameter("case", caseId)
        .executeUpdate();
    return id;
  }

  @Transactional
  void seedDisputeEvent(String disputeId, String text) {
    em.createNativeQuery(
            "INSERT INTO dispute_event (id, dispute_id, text, actor, at)"
                + " VALUES (:id, :did, :text, 'system', now())")
        .setParameter("id", "de-" + System.nanoTime())
        .setParameter("did", disputeId)
        .setParameter("text", text)
        .executeUpdate();
  }

  /** Drain a creator's available balance by posting a cleared reserve DEBIT on creator_payable. */
  @Transactional
  void withdrawAll(String creator, long n, long amountMinor) {
    // Get-or-create the creator_payable + payout_clearing accounts, then post a balanced cleared
    // reserve (DEBIT creator_payable / CREDIT payout_clearing) so available drops to zero — the same
    // shape WU-PAY-4's withdrawal reservation produces, without needing a KYC/method fixture here.
    String cp = accountId("creator_payable", creator);
    String pc = accountId("payout_clearing", null);
    String txn = "rf-wd-txn-" + n;
    em.createNativeQuery(
            "INSERT INTO ledger_posting (ref_type, ref_id, txn_id, posted_at)"
                + " VALUES ('withdraw', :wd, :txn, now())")
        .setParameter("wd", "rf-wd-" + n)
        .setParameter("txn", txn)
        .executeUpdate();
    insertEntry(txn, cp, "DEBIT", amountMinor, "withdraw", "rf-wd-" + n);
    insertEntry(txn, pc, "CREDIT", amountMinor, "withdraw", "rf-wd-" + n);
    refreshBalance(creator);
  }

  @Transactional
  String accountId(String kind, String owner) {
    java.util.List<?> found =
        (owner == null
                ? em.createNativeQuery(
                        "SELECT id FROM ledger_account WHERE kind = :k AND owner_account_id IS NULL")
                    .setParameter("k", kind)
                : em.createNativeQuery(
                        "SELECT id FROM ledger_account WHERE kind = :k AND owner_account_id = :o")
                    .setParameter("k", kind)
                    .setParameter("o", owner))
            .getResultList();
    if (!found.isEmpty()) {
      return (String) found.get(0);
    }
    String id = "la-" + System.nanoTime();
    em.createNativeQuery(
            "INSERT INTO ledger_account (id, kind, owner_account_id, created_at)"
                + " VALUES (:id, :k, :o, now()) ON CONFLICT DO NOTHING")
        .setParameter("id", id)
        .setParameter("k", kind)
        .setParameter("o", owner)
        .executeUpdate();
    return accountId(kind, owner);
  }

  private void insertEntry(
      String txn, String account, String direction, long amount, String refType, String refId) {
    em.createNativeQuery(
            "INSERT INTO ledger_entry (id, txn_id, account_id, direction, amount_minor, ref_type,"
                + " ref_id, cleared_at, posted_at)"
                + " VALUES (:id, :txn, :acc, :dir, :amt, :rt, :ri, now(), now())")
        .setParameter("id", "le-" + System.nanoTime() + "-" + direction)
        .setParameter("txn", txn)
        .setParameter("acc", account)
        .setParameter("dir", direction)
        .setParameter("amt", amount)
        .setParameter("rt", refType)
        .setParameter("ri", refId)
        .executeUpdate();
  }

  @Transactional
  void refreshBalance(String creator) {
    Object[] agg =
        (Object[])
            em.createNativeQuery(
                    "SELECT "
                        + " COALESCE(SUM(CASE WHEN e.direction='CREDIT' AND e.cleared_at IS NOT NULL THEN e.amount_minor "
                        + "                   WHEN e.direction='DEBIT'  AND e.cleared_at IS NOT NULL THEN -e.amount_minor ELSE 0 END),0), "
                        + " COALESCE(SUM(CASE WHEN e.direction='CREDIT' AND e.cleared_at IS NULL THEN e.amount_minor ELSE 0 END),0), "
                        + " COALESCE(SUM(CASE WHEN e.direction='CREDIT' THEN e.amount_minor ELSE 0 END),0) "
                        + "FROM ledger_entry e JOIN ledger_account a ON a.id = e.account_id "
                        + "WHERE a.kind = 'creator_payable' AND a.owner_account_id = :o")
                .setParameter("o", creator)
                .getSingleResult();
    em.createNativeQuery(
            "INSERT INTO creator_balance (account_id, available_minor, pending_minor, lifetime_minor, updated_at)"
                + " VALUES (:id, :av, :pd, :lf, now())"
                + " ON CONFLICT (account_id) DO UPDATE SET available_minor = EXCLUDED.available_minor,"
                + " pending_minor = EXCLUDED.pending_minor, lifetime_minor = EXCLUDED.lifetime_minor,"
                + " updated_at = EXCLUDED.updated_at")
        .setParameter("id", creator)
        .setParameter("av", ((Number) agg[0]).longValue())
        .setParameter("pd", ((Number) agg[1]).longValue())
        .setParameter("lf", ((Number) agg[2]).longValue())
        .executeUpdate();
  }

  // ---- read helpers ----

  @Transactional
  String orderStatus(String reference) {
    return (String)
        em.createNativeQuery("SELECT status FROM \"order\" WHERE reference = :ref")
            .setParameter("ref", reference)
            .getSingleResult();
  }

  @Transactional
  String intentOfOrder(String reference) {
    return (String)
        em.createNativeQuery("SELECT payment_intent_id FROM \"order\" WHERE reference = :ref")
            .setParameter("ref", reference)
            .getSingleResult();
  }

  @Transactional
  String providerRefOf(String intentId) {
    return (String)
        em.createNativeQuery("SELECT provider_ref FROM payment_intent WHERE id = :id")
            .setParameter("id", intentId)
            .getSingleResult();
  }

  @Transactional
  String accountIdOf(String email) {
    return (String)
        em.createNativeQuery("SELECT id FROM account WHERE email = :e")
            .setParameter("e", email)
            .getSingleResult();
  }

  @Transactional
  long activeGrantCount(String accountId, String trackId) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM ownership_grant WHERE account_id = :acc"
                        + " AND track_id = :tid AND revoked_at IS NULL")
                .setParameter("acc", accountId)
                .setParameter("tid", trackId)
                .getSingleResult())
        .longValue();
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
  long ledgerImbalance() {
    Object v =
        em.createNativeQuery(
                "SELECT COALESCE(SUM(CASE WHEN direction='DEBIT' THEN amount_minor"
                    + " ELSE -amount_minor END), 0) FROM ledger_entry")
            .getSingleResult();
    return ((Number) v).longValue();
  }

  @Transactional
  long refundCount(String disputeId) {
    return ((Number)
            em.createNativeQuery("SELECT COUNT(*) FROM refund WHERE dispute_id = :d")
                .setParameter("d", disputeId)
                .getSingleResult())
        .longValue();
  }

  @Transactional
  long refundCountForIntent(String intentId) {
    return ((Number)
            em.createNativeQuery("SELECT COUNT(*) FROM refund WHERE payment_intent_id = :i")
                .setParameter("i", intentId)
                .getSingleResult())
        .longValue();
  }
}
