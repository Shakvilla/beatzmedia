package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * The complete admin authorization matrix: every {@code /v1/admin} endpoint against every admin
 * role.
 *
 * <p><strong>Why this exists.</strong> Role coverage was real but uneven — spread across ten IT
 * files, each testing the roles its own feature cared about. {@code AdminRiskResourceIT} exercised
 * only {@code moderator}; {@code AdminModerationResourceIT} carried a single 403 assertion;
 * {@code AdminCatalogResourceIT} never checked that {@code finance} or {@code editor} are refused.
 * Nothing asserted the matrix as a whole, so a widened annotation — {@code moderator} quietly
 * becoming {@code moderator, editor} — could not fail any test.
 *
 * <p>This asserts both directions for all 61 endpoints × 5 roles. A permitted role must NOT get 403
 * (any other status is fine: 404 for the deliberately non-existent ids used throughout, 400 for a
 * missing idempotency key, 422 for a missing body field — all prove authorization passed). A
 * non-permitted role MUST get exactly 403.
 *
 * <p><strong>Safety.</strong> Every {@code {id}} is {@code qa-nonexistent}, and the two money POSTs
 * are sent WITHOUT an {@code Idempotency-Key} so a permitted role stops at 400 rather than actually
 * running payouts. No request in this class can mutate real data.
 *
 * <p>All mismatches are collected and reported together — a failure shows the whole picture rather
 * than the first cell that broke.
 */
@QuarkusTest
@Tag("integration")
class AdminRoleMatrixIT {

  private static final String PASSWORD = "password123";
  private static final String NO_ID = "qa-nonexistent";

  private static final String SUPER = "super-admin";
  private static final String FINANCE = "finance";
  private static final String MOD = "moderator";
  private static final String EDITOR = "editor";
  private static final String SUPPORT = "support";

  private static final List<String> ALL_ROLES = List.of(SUPER, FINANCE, MOD, EDITOR, SUPPORT);

  /** Tokens are minted once for the class: five signups + role grants is the expensive part. */
  private static Map<String, String> tokens;

  @Inject EntityManager em;

  /** One endpoint and the exact set of roles permitted to call it. */
  private record Rule(String verb, String path, Set<String> allowed, String body) {
    Rule(String verb, String path, Set<String> allowed) {
      this(verb, path, allowed, null);
    }
  }

  private static final Set<String> EVERY_ADMIN = Set.of(SUPER, FINANCE, MOD, EDITOR, SUPPORT);
  private static final Set<String> SUPER_ONLY = Set.of(SUPER);
  private static final Set<String> MOD_SUPER = Set.of(MOD, SUPER);
  private static final Set<String> MOD_SUPER_SUPPORT = Set.of(MOD, SUPER, SUPPORT);
  private static final Set<String> FIN_SUPER = Set.of(FINANCE, SUPER);
  private static final Set<String> ED_SUPER = Set.of(EDITOR, SUPER);
  private static final Set<String> ED_SUPER_SUPPORT = Set.of(EDITOR, SUPER, SUPPORT);
  /**
   * Support ticket <em>mutations</em> (GAP-17). Reads stay {@link #EVERY_ADMIN}: looking a ticket up
   * is how a finance admin corroborates a refund complaint or an editor traces a takedown appeal.
   * Acting on one speaks to a fan in the platform's voice, which is a narrower job.
   */
  private static final Set<String> SUPPORT_SUPER = Set.of(SUPPORT, SUPER);

  /**
   * The expected matrix, transcribed from the {@code @RolesAllowed} annotations on the resources.
   *
   * <p>Deliberately written out by hand rather than derived from the annotations at runtime: a test
   * that reads the same annotation it is checking proves only that reflection works. This is the
   * independent statement of intent that the code must match.
   */
  private static List<Rule> matrix() {
    List<Rule> r = new ArrayList<>();

    r.add(new Rule("GET", "/v1/admin/overview", EVERY_ADMIN));
    r.add(new Rule("GET", "/v1/admin/health", EVERY_ADMIN));
    r.add(new Rule("GET", "/v1/admin/audit", SUPER_ONLY));

    // Catalog moderation: support may look, only moderators may act.
    r.add(new Rule("GET", "/v1/admin/catalog", MOD_SUPER_SUPPORT));
    r.add(new Rule("GET", "/v1/admin/catalog/" + NO_ID, MOD_SUPER_SUPPORT));
    r.add(new Rule("POST", "/v1/admin/catalog/" + NO_ID + "/approve", MOD_SUPER, "{}"));
    r.add(new Rule("POST", "/v1/admin/catalog/" + NO_ID + "/flag", MOD_SUPER, "{\"note\":\"qa\"}"));
    r.add(new Rule("POST", "/v1/admin/catalog/" + NO_ID + "/takedown", MOD_SUPER,
        "{\"reason\":\"qa\"}"));
    r.add(new Rule("POST", "/v1/admin/catalog/" + NO_ID + "/reinstate", MOD_SUPER, "{}"));

    r.add(new Rule("GET", "/v1/admin/moderation", MOD_SUPER_SUPPORT));
    for (String a : List.of("approve", "dismiss", "escalate", "remove", "review")) {
      r.add(new Rule("POST", "/v1/admin/moderation/" + NO_ID + "/" + a, MOD_SUPER, "{}"));
    }

    r.add(new Rule("GET", "/v1/admin/risk", MOD_SUPER));
    r.add(new Rule("POST", "/v1/admin/risk/" + NO_ID + "/ban", MOD_SUPER, "{\"reason\":\"qa\"}"));
    r.add(new Rule("POST", "/v1/admin/risk/" + NO_ID + "/clear", MOD_SUPER, "{}"));
    r.add(new Rule("POST", "/v1/admin/risk/" + NO_ID + "/review", MOD_SUPER, "{}"));

    // Finance is ring-fenced: no moderator, editor or support anywhere near money.
    r.add(new Rule("GET", "/v1/admin/finance", FIN_SUPER));
    r.add(new Rule("GET", "/v1/admin/finance/ledger", FIN_SUPER));
    r.add(new Rule("GET", "/v1/admin/finance/payouts", FIN_SUPER));
    r.add(new Rule("GET", "/v1/admin/finance/disputes/" + NO_ID, FIN_SUPER));
    for (String a : List.of("escalate", "refund", "reject")) {
      r.add(new Rule("POST", "/v1/admin/finance/disputes/" + NO_ID + "/" + a, FIN_SUPER,
          "{\"reason\":\"qa\"}"));
    }
    // No Idempotency-Key on purpose — a permitted role must stop at 400, never actually pay out.
    r.add(new Rule("POST", "/v1/admin/finance/payouts/run-weekly", FIN_SUPER, "{}"));
    r.add(new Rule("POST", "/v1/admin/finance/payouts/" + NO_ID + "/send", FIN_SUPER, "{}"));

    r.add(new Rule("GET", "/v1/admin/editorial/featured", ED_SUPER_SUPPORT));
    r.add(new Rule("PUT", "/v1/admin/editorial/featured", ED_SUPER, "[]"));
    r.add(new Rule("GET", "/v1/admin/editorial/playlists", ED_SUPER_SUPPORT));
    r.add(new Rule("POST", "/v1/admin/editorial/playlists", ED_SUPER, "{}"));
    r.add(new Rule("GET", "/v1/admin/editorial/push", ED_SUPER_SUPPORT));
    r.add(new Rule("POST", "/v1/admin/editorial/push", ED_SUPER, "{}"));

    r.add(new Rule("GET", "/v1/admin/compliance", SUPER_ONLY));
    for (String a : List.of("start", "complete", "export", "notice")) {
      r.add(new Rule("POST", "/v1/admin/compliance/" + NO_ID + "/" + a, SUPER_ONLY, "{}"));
    }

    r.add(new Rule("GET", "/v1/admin/support/tickets", EVERY_ADMIN));
    r.add(new Rule("GET", "/v1/admin/support/tickets/" + NO_ID, EVERY_ADMIN));
    r.add(new Rule("POST", "/v1/admin/support/tickets/" + NO_ID + "/assign", SUPPORT_SUPER,
        "{\"assigneeId\":\"qa\"}"));
    r.add(new Rule("POST", "/v1/admin/support/tickets/" + NO_ID + "/reply", SUPPORT_SUPER,
        "{\"text\":\"qa\"}"));
    r.add(new Rule("POST", "/v1/admin/support/tickets/" + NO_ID + "/resolve", SUPPORT_SUPER, "{}"));

    r.add(new Rule("GET", "/v1/admin/users", EVERY_ADMIN));
    r.add(new Rule("GET", "/v1/admin/users/" + NO_ID, EVERY_ADMIN));
    r.add(new Rule("POST", "/v1/admin/users/" + NO_ID + "/verify", MOD_SUPER, "{}"));
    r.add(new Rule("POST", "/v1/admin/users/" + NO_ID + "/suspend", MOD_SUPER,
        "{\"reason\":\"qa\"}"));
    r.add(new Rule("POST", "/v1/admin/users/" + NO_ID + "/reactivate", MOD_SUPER, "{}"));
    r.add(new Rule("POST", "/v1/admin/users/" + NO_ID + "/data-export", Set.of(SUPER, SUPPORT),
        "{}"));
    r.add(new Rule("POST", "/v1/admin/users/" + NO_ID + "/impersonate", SUPER_ONLY, "{}"));

    r.add(new Rule("GET", "/v1/admin/taxonomy?kind=genre", EVERY_ADMIN));
    r.add(new Rule("POST", "/v1/admin/taxonomy", SUPER_ONLY,
        "{\"kind\":\"genre\",\"label\":\"QA\"}"));
    r.add(new Rule("PATCH", "/v1/admin/taxonomy/" + NO_ID, SUPER_ONLY, "{\"label\":\"QA\"}"));
    r.add(new Rule("DELETE", "/v1/admin/taxonomy/" + NO_ID, SUPER_ONLY));

    r.add(new Rule("GET", "/v1/admin/team", EVERY_ADMIN));
    r.add(new Rule("POST", "/v1/admin/team/invite", SUPER_ONLY,
        "{\"email\":\"qa-matrix@example.com\",\"role\":\"support\"}"));
    r.add(new Rule("PATCH", "/v1/admin/team/" + NO_ID, SUPER_ONLY, "{\"role\":\"support\"}"));
    r.add(new Rule("DELETE", "/v1/admin/team/" + NO_ID, SUPER_ONLY));

    r.add(new Rule("GET", "/v1/admin/settings", SUPER_ONLY));
    r.add(new Rule("PUT", "/v1/admin/settings", SUPER_ONLY, "{}"));

    return r;
  }

  @Test
  void every_admin_endpoint_admits_exactly_its_permitted_roles() {
    ensureTokens();
    List<String> failures = new ArrayList<>();

    for (Rule rule : matrix()) {
      for (String role : ALL_ROLES) {
        int status = call(rule, tokens.get(role));
        boolean permitted = rule.allowed().contains(role);

        if (permitted && status == 403) {
          failures.add("DENIED but should be allowed: %s %s as %s"
              .formatted(rule.verb(), rule.path(), role));
        } else if (!permitted && status != 403) {
          failures.add("ALLOWED but should be denied: %s %s as %s -> %d"
              .formatted(rule.verb(), rule.path(), role, status));
        }
      }
    }

    assertTrue(failures.isEmpty(),
        () -> "%d authorization mismatches:\n  %s".formatted(
            failures.size(), String.join("\n  ", failures)));
  }

  /** An admin token is not a fan token: no admin role must reach a fan-only surface by accident. */
  @Test
  void admin_endpoints_reject_a_plain_fan_token() {
    ensureTokens();
    String fan = signupAndLogin("matrix-fan-" + System.nanoTime() + "@example.com");
    List<String> failures = new ArrayList<>();

    for (Rule rule : matrix()) {
      int status = call(rule, fan);
      if (status != 403) {
        failures.add("%s %s -> %d (expected 403 for a non-admin)"
            .formatted(rule.verb(), rule.path(), status));
      }
    }
    assertTrue(failures.isEmpty(),
        () -> "%d endpoints reachable by a plain fan:\n  %s".formatted(
            failures.size(), String.join("\n  ", failures)));
  }

  private int call(Rule rule, String token) {
    var req = given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    if (rule.body() != null) {
      req = req.body(rule.body());
    }
    Response res = switch (rule.verb()) {
      case "GET" -> req.when().get(rule.path());
      case "POST" -> req.when().post(rule.path());
      case "PUT" -> req.when().put(rule.path());
      case "PATCH" -> req.when().patch(rule.path());
      case "DELETE" -> req.when().delete(rule.path());
      default -> throw new IllegalArgumentException("verb " + rule.verb());
    };
    return res.getStatusCode();
  }

  private void ensureTokens() {
    if (tokens != null) {
      return;
    }
    Map<String, String> minted = new LinkedHashMap<>();
    for (String role : ALL_ROLES) {
      long n = System.nanoTime();
      String email = "matrix-" + role + "-" + n + "@example.com";
      String accountId = signup(email);
      grantAdminRole(accountId, role, n);
      minted.put(role, login(email));
    }
    tokens = minted;
  }

  private String signup(String email) {
    return given().contentType(ContentType.JSON)
        .body("{\"name\":\"Matrix Admin\",\"email\":\"%s\",\"password\":\"%s\"}"
            .formatted(email, PASSWORD))
        .when().post("/v1/auth/signup")
        .then().statusCode(201)
        .extract().jsonPath().getString("account.id");
  }

  private String signupAndLogin(String email) {
    signup(email);
    return login(email);
  }

  private String login(String email) {
    return given().contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
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
        .setParameter("memberId", "matrix-member-" + role + "-" + n)
        .setParameter("accountId", accountId)
        .setParameter("role", role)
        .executeUpdate();
  }
}
