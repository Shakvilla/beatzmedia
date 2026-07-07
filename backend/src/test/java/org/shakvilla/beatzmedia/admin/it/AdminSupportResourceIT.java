package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for {@link org.shakvilla.beatzmedia.admin.adapter.in.rest.AdminSupportResource}
 * (WU-ADM-7). Testcontainers Postgres + REST-assured; Flyway migrates at start. Covers
 * LLFR-ADMIN-08.1: inbox list, thread detail, reply, assign, resolve, RBAC (support+ = every
 * admin role, fans forbidden), and exactly-one-AuditEntry-per-mutation (INV-10).
 */
@QuarkusTest
@Tag("integration")
class AdminSupportResourceIT {

  private static final String PASSWORD = "password123";
  private static final String TICKETS_URL = "/v1/admin/support/tickets";

  @Inject EntityManager em;

  // ---- LLFR-ADMIN-08.1: GET /admin/support/tickets?status=&q= ----

  @Test
  void list_tickets_as_any_admin_role_returns_200_with_thread() {
    long n = System.nanoTime();
    String requesterAccount = signUpFan("list-fan-" + n + "@example.com");
    String ticketId = seedTicket(n, "List test subject", requesterAccount, "open", "high");
    seedMessage(ticketId, "user", "Fan " + n, "Help please");

    String supportToken = supportToken(n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .when().get(TICKETS_URL)
        .then().statusCode(200)
        .body("find { it.id == '" + ticketId + "' }.subject", equalTo("List test subject"))
        .body("find { it.id == '" + ticketId + "' }.messages.size()", greaterThanOrEqualTo(1));
  }

  @Test
  void list_tickets_filters_by_status() {
    long n = System.nanoTime();
    String requesterAccount = signUpFan("filter-fan-" + n + "@example.com");
    String openId = seedTicket(n, "Open ticket " + n, requesterAccount, "open", "normal");
    String resolvedId = seedTicket(n + 1, "Resolved ticket " + n, requesterAccount, "resolved", "low");

    String supportToken = supportToken(n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .queryParam("status", "resolved")
        .when().get(TICKETS_URL)
        .then().statusCode(200)
        .body("id", org.hamcrest.Matchers.hasItem(resolvedId))
        .body("id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(openId)));
  }

  @Test
  void list_tickets_as_non_admin_returns_403() {
    long n = System.nanoTime();
    String fanToken = signUpFanToken("noadmin-" + n + "@example.com");
    given()
        .header("Authorization", "Bearer " + fanToken)
        .when().get(TICKETS_URL)
        .then().statusCode(403);
  }

  @Test
  void list_tickets_without_token_returns_401() {
    given()
        .when().get(TICKETS_URL)
        .then().statusCode(401);
  }

  // ---- LLFR-ADMIN-08.1: GET /admin/support/tickets/:id (thread) ----

  @Test
  void get_ticket_returns_detail_with_thread() {
    long n = System.nanoTime();
    String requesterAccount = signUpFan("get-fan-" + n + "@example.com");
    String ticketId = seedTicket(n, "Get test subject", requesterAccount, "open", "normal");
    seedMessage(ticketId, "user", "Fan", "First message");

    String supportToken = supportToken(n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .when().get(TICKETS_URL + "/" + ticketId)
        .then().statusCode(200)
        .body("id", equalTo(ticketId))
        .body("subject", equalTo("Get test subject"))
        .body("messages.size()", equalTo(1))
        .body("messages[0].from", equalTo("user"));
  }

  @Test
  void get_unknown_ticket_returns_404() {
    String supportToken = supportToken(System.nanoTime());
    given()
        .header("Authorization", "Bearer " + supportToken)
        .when().get(TICKETS_URL + "/does-not-exist")
        .then().statusCode(404);
  }

  // ---- LLFR-ADMIN-08.1: POST /admin/support/tickets/:id/reply { text } ----

  @Test
  void reply_appends_message_and_records_audit_entry() {
    long n = System.nanoTime();
    String requesterAccount = signUpFan("reply-fan-" + n + "@example.com");
    String ticketId = seedTicket(n, "Reply test subject", requesterAccount, "open", "normal");
    String supportToken = supportToken(n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .contentType(ContentType.JSON)
        .body("{\"text\":\"We're looking into this.\"}")
        .when().post(TICKETS_URL + "/" + ticketId + "/reply")
        .then().statusCode(201)
        .body("text", equalTo("We're looking into this."))
        .body("from", equalTo("agent"))
        .body("id", notNullValue());

    // The ticket moves open -> pending after an agent reply.
    given()
        .header("Authorization", "Bearer " + supportToken)
        .when().get(TICKETS_URL + "/" + ticketId)
        .then().statusCode(200)
        .body("status", equalTo("pending"))
        .body("messages.size()", equalTo(1));

    assertEquals(1, countAuditEntriesFor(ticketId, "Replied to ticket"),
        "exactly one AuditEntry per mutation (INV-10)");
  }

  @Test
  void reply_with_blank_text_returns_422_and_no_state_change_or_audit() {
    long n = System.nanoTime();
    String requesterAccount = signUpFan("blank-fan-" + n + "@example.com");
    String ticketId = seedTicket(n, "Blank reply subject", requesterAccount, "open", "normal");
    String supportToken = supportToken(n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .contentType(ContentType.JSON)
        .body("{\"text\":\"\"}")
        .when().post(TICKETS_URL + "/" + ticketId + "/reply")
        .then().statusCode(422);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .when().get(TICKETS_URL + "/" + ticketId)
        .then().statusCode(200)
        .body("status", equalTo("open"))
        .body("messages.size()", equalTo(0));

    assertEquals(0, countAuditEntriesFor(ticketId, "Replied to ticket"),
        "no audit row written for a rejected (blank) reply");
  }

  @Test
  void reply_to_unknown_ticket_returns_404() {
    String supportToken = supportToken(System.nanoTime());
    given()
        .header("Authorization", "Bearer " + supportToken)
        .contentType(ContentType.JSON)
        .body("{\"text\":\"hello\"}")
        .when().post(TICKETS_URL + "/does-not-exist/reply")
        .then().statusCode(404);
  }

  @Test
  void reply_as_non_admin_returns_403() {
    long n = System.nanoTime();
    String requesterAccount = signUpFan("reply403-fan-" + n + "@example.com");
    String ticketId = seedTicket(n, "403 subject", requesterAccount, "open", "normal");
    String fanToken = signUpFanToken("reply403-caller-" + n + "@example.com");

    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body("{\"text\":\"hello\"}")
        .when().post(TICKETS_URL + "/" + ticketId + "/reply")
        .then().statusCode(403);
  }

  // ---- LLFR-ADMIN-08.1: POST /admin/support/tickets/:id/assign { assigneeId } ----

  @Test
  void assign_sets_assignee_and_records_audit_entry() {
    long n = System.nanoTime();
    String requesterAccount = signUpFan("assign-fan-" + n + "@example.com");
    String ticketId = seedTicket(n, "Assign test subject", requesterAccount, "open", "normal");
    String supportToken = supportToken(n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .contentType(ContentType.JSON)
        .body("{\"assigneeId\":\"member-xyz\"}")
        .when().post(TICKETS_URL + "/" + ticketId + "/assign")
        .then().statusCode(200)
        .body("id", equalTo(ticketId));

    assertEquals(1, countAuditEntriesFor(ticketId, "Assigned ticket to member-xyz"),
        "exactly one AuditEntry per mutation (INV-10)");
  }

  @Test
  void assign_unknown_ticket_returns_404() {
    String supportToken = supportToken(System.nanoTime());
    given()
        .header("Authorization", "Bearer " + supportToken)
        .contentType(ContentType.JSON)
        .body("{\"assigneeId\":\"member-1\"}")
        .when().post(TICKETS_URL + "/does-not-exist/assign")
        .then().statusCode(404);
  }

  // ---- LLFR-ADMIN-08.1: POST /admin/support/tickets/:id/resolve ----

  @Test
  void resolve_transitions_status_and_records_audit_entry() {
    long n = System.nanoTime();
    String requesterAccount = signUpFan("resolve-fan-" + n + "@example.com");
    String ticketId = seedTicket(n, "Resolve test subject", requesterAccount, "open", "normal");
    String supportToken = supportToken(n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .when().post(TICKETS_URL + "/" + ticketId + "/resolve")
        .then().statusCode(200)
        .body("status", equalTo("resolved"));

    assertEquals(1, countAuditEntriesFor(ticketId, "Resolved ticket"),
        "exactly one AuditEntry per mutation (INV-10)");
  }

  @Test
  void resolve_already_resolved_ticket_returns_409() {
    long n = System.nanoTime();
    String requesterAccount = signUpFan("resolve409-fan-" + n + "@example.com");
    String ticketId = seedTicket(n, "Already resolved subject", requesterAccount, "resolved", "normal");
    String supportToken = supportToken(n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .when().post(TICKETS_URL + "/" + ticketId + "/resolve")
        .then().statusCode(409);

    assertEquals(0, countAuditEntriesFor(ticketId, "Resolved ticket"),
        "no audit row for a rejected (already-resolved) mutation");
  }

  @Test
  void resolve_unknown_ticket_returns_404() {
    String supportToken = supportToken(System.nanoTime());
    given()
        .header("Authorization", "Bearer " + supportToken)
        .when().post(TICKETS_URL + "/does-not-exist/resolve")
        .then().statusCode(404);
  }

  @Test
  void resolve_as_non_admin_returns_403() {
    long n = System.nanoTime();
    String requesterAccount = signUpFan("resolve403-fan-" + n + "@example.com");
    String ticketId = seedTicket(n, "403 resolve subject", requesterAccount, "open", "normal");
    String fanToken = signUpFanToken("resolve403-caller-" + n + "@example.com");

    given()
        .header("Authorization", "Bearer " + fanToken)
        .when().post(TICKETS_URL + "/" + ticketId + "/resolve")
        .then().statusCode(403);
  }

  // ---- Full RBAC matrix: every admin role can act on support tickets (admin ADD §8) ----

  @Test
  void every_admin_role_can_resolve_a_ticket() {
    for (String role : new String[] {"super-admin", "finance", "moderator", "editor", "support"}) {
      long n = System.nanoTime();
      String requesterAccount = signUpFan("rbac-fan-" + role + "-" + n + "@example.com");
      String ticketId = seedTicket(n, "RBAC subject " + role, requesterAccount, "open", "normal");
      String token = adminToken(role, n);

      given()
          .header("Authorization", "Bearer " + token)
          .when().post(TICKETS_URL + "/" + ticketId + "/resolve")
          .then().statusCode(200)
          .body("status", equalTo("resolved"));
    }
  }

  // ================================ helpers =====================================

  private String signUpFan(String email) {
    signUpFanToken(email);
    return accountIdOf(email);
  }

  private String signUpFanToken(String email) {
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"IT Fan\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/signup");
    return given()
        .contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private String supportToken(long n) {
    return adminToken("support", n);
  }

  private String adminToken(String role, long n) {
    String email = "adm-" + role + "-" + n + "@example.com";
    var signup = given().contentType(ContentType.JSON)
        .body("{\"name\":\"IT Admin\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/signup").then().statusCode(201).extract().jsonPath();
    grantAdminRole(signup.getString("account.id"), role, n);
    return given().contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login").then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  @Transactional
  void grantAdminRole(String accountId, String role, long n) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, :role, now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("memberId", "adm-member-" + role + "-" + n)
        .setParameter("accountId", accountId)
        .setParameter("role", role)
        .executeUpdate();
  }

  @Transactional
  String accountIdOf(String email) {
    return (String) em.createNativeQuery("SELECT id FROM account WHERE email = :e")
        .setParameter("e", email)
        .getSingleResult();
  }

  @Transactional
  String seedTicket(long n, String subject, String requesterRef, String status, String priority) {
    String id = "it-ticket-" + n;
    em.createNativeQuery(
            "INSERT INTO support_ticket (id, subject, requester_ref, channel, priority, status, created_at) "
                + "VALUES (:id, :subject, :requester, 'email', :priority, :status, now())")
        .setParameter("id", id)
        .setParameter("subject", subject)
        .setParameter("requester", requesterRef)
        .setParameter("priority", priority)
        .setParameter("status", status)
        .executeUpdate();
    return id;
  }

  @Transactional
  void seedMessage(String ticketId, String fromParty, String author, String body) {
    em.createNativeQuery(
            "INSERT INTO support_message (id, ticket_id, from_party, author, body, created_at) "
                + "VALUES (:id, :ticketId, :from, :author, :body, now())")
        .setParameter("id", "it-msg-" + System.nanoTime())
        .setParameter("ticketId", ticketId)
        .setParameter("from", fromParty)
        .setParameter("author", author)
        .setParameter("body", body)
        .executeUpdate();
  }

  @Transactional
  long countAuditEntriesFor(String targetId, String action) {
    return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM audit_entry WHERE target_id = :tid AND action = :action")
        .setParameter("tid", targetId)
        .setParameter("action", action)
        .getSingleResult())
        .longValue();
  }
}
