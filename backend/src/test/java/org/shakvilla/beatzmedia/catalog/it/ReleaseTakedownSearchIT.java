package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * GAP-27 — a taken-down release must not remain in the fan-facing search index.
 *
 * <p><strong>What was wrong.</strong> Takedown hid the release's <em>track</em> documents and
 * deleted the {@code album} row, but left the <em>album</em> search document exactly as it was —
 * same {@code indexed_at}, still {@code visible = true}. Observed on a live takedown:
 *
 * <pre>
 * GET /v1/search?q=Test  ->  topResult: { entityType: "ALBUM", title: "Test", ... }
 * GET /v1/albums/{id}    ->  404 ALBUM_NOT_FOUND
 * </pre>
 *
 * <p>A release pulled for a copyright claim stayed the top result in public search and dead-ended
 * on a 404, indefinitely: the periodic reindex loads albums from the {@code album} table, which no
 * longer held the row, and indexing is upsert-only — a document nothing loads is a document nothing
 * can correct.
 *
 * <p><strong>Why this test is an IT and not a unit test.</strong> The unit tests for the album
 * projection all passed throughout. The defect lived in what the projection <em>did not do</em>, and
 * only a test that drives the real admin endpoints and then reads {@code search_document} and
 * {@code /v1/search} can see it. Asserting on the public search response as well as the table is
 * deliberate — the table is the mechanism, the endpoint is the promise.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReleaseTakedownSearchIT {

  private static final String PASSWORD = "password123";
  private static final String CATALOG_URL = "/v1/admin/catalog";

  @Inject AgroalDataSource dataSource;
  @Inject EntityManager em;

  private static String moderatorToken;
  private static String releaseId;
  private static String title;

  @Test
  @Order(1)
  void setup_and_publish_a_release() {
    long n = System.nanoTime();
    title = "Takedown Search Probe " + n;

    String artistEmail = "tds-it-artist-" + n + "@example.com";
    signUp(artistEmail, "IT Artist");
    String artistToken = login(artistEmail);
    given().header("Authorization", "Bearer " + artistToken)
        .when().post("/v1/me/become-artist")
        .then().statusCode(200);
    artistToken = login(artistEmail);
    seedArtistProfile(artistToken, "IT Artist");

    moderatorToken = adminToken("moderator", n);

    String trackId = "tds-it-track-" + n;
    seedReadyTrackForArtist(trackId, accountIdFromToken(artistToken));

    releaseId = "tds-it-release-" + n;
    seedInReviewReleaseWithTrack(releaseId, accountIdFromToken(artistToken), title, trackId);

    given().header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON).body("{}")
        .when().post(CATALOG_URL + "/" + releaseId + "/approve")
        .then().statusCode(200);
  }

  @Test
  @Order(2)
  void a_live_release_is_an_album_in_the_index_and_in_search() {
    assertTrue(albumRowExists(), "publishing should project the album row");
    assertTrue(albumDocumentIsVisible(), "publishing should index the album");
    assertTrue(searchReturnsTheAlbum(), "a live release should be findable");
  }

  @Test
  @Order(3)
  void takedown_removes_the_album_from_the_index_and_from_search() {
    given().header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON).body("{\"reason\":\"Copyright claim\"}")
        .when().post(CATALOG_URL + "/" + releaseId + "/takedown")
        .then().statusCode(200);

    assertFalse(albumRowExists(), "takedown already deleted the album row — that part was fine");
    assertFalse(
        albumDocumentIsVisible(),
        "GAP-27: the album search document outlived the album row and stayed visible");
    assertFalse(
        searchReturnsTheAlbum(),
        "a release pulled for a copyright claim must not still be returned by public search");
  }

  @Test
  @Order(4)
  void reinstate_puts_it_back() {
    given().header("Authorization", "Bearer " + moderatorToken)
        .when().post(CATALOG_URL + "/" + releaseId + "/reinstate")
        .then().statusCode(200);

    assertTrue(albumRowExists(), "reinstate should re-project the album row");
    assertTrue(albumDocumentIsVisible(), "reinstate should re-index the album");
    assertTrue(searchReturnsTheAlbum(), "a reinstated release should be findable again");
  }

  // ================================ helpers =====================================

  /** True when an ALBUM document exists for this release AND is visible to search. */
  private boolean albumDocumentIsVisible() {
    return querySingleBoolean(
        "SELECT EXISTS (SELECT 1 FROM search_document"
            + " WHERE entity_type = 'ALBUM' AND entity_id = ? AND visible = true)",
        releaseId);
  }

  private boolean albumRowExists() {
    return querySingleBoolean("SELECT EXISTS (SELECT 1 FROM album WHERE id = ?)", releaseId);
  }

  /**
   * Drives the public endpoint a fan would hit, not just the table behind it — and checks
   * {@code topResult} as well as the grouped {@code albums} list.
   *
   * <p>Checking only {@code albums} would have missed the bug entirely. {@code SearchService}
   * hydrates that list from the {@code album} table and drops hits it cannot resolve, so a deleted
   * album silently vanished from it. {@code topResult} is mapped straight off the search hit with no
   * hydration, so that is where the stale document actually surfaced — as the single most prominent
   * result on the page.
   */
  private boolean searchReturnsTheAlbum() {
    var json =
        given().queryParam("q", title)
            .when().get("/v1/search")
            .then().statusCode(200)
            .extract().jsonPath();
    List<String> albumIds = json.getList("albums.id", String.class);
    boolean inAlbums = albumIds != null && albumIds.contains(releaseId);
    boolean isTopResult = releaseId.equals(json.getString("topResult.entityId"));
    return inAlbums || isTopResult;
  }

  private boolean querySingleBoolean(String sql, String param) {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, param);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && rs.getBoolean(1);
      }
    } catch (Exception e) {
      throw new RuntimeException("query failed: " + sql, e);
    }
  }

  private void signUp(String email, String name) {
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, PASSWORD))
        .when().post("/v1/auth/signup")
        .then().statusCode(201);
  }

  private String login(String email) {
    return given()
        .contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private String adminToken(String role, long n) {
    String email = "tds-it-" + role + "-" + n + "@example.com";
    var signup = given().contentType(ContentType.JSON)
        .body("{\"name\":\"IT Admin\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/signup").then().statusCode(201).extract().jsonPath();
    grantAdminRole(signup.getString("account.id"), role, n);
    return login(email);
  }

  @Transactional
  void grantAdminRole(String accountId, String role, long n) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, :role, now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("memberId", "tds-it-member-" + role + "-" + n)
        .setParameter("accountId", accountId)
        .setParameter("role", role)
        .executeUpdate();
  }

  private void seedArtistProfile(String token, String name) {
    String accountId = accountIdFromToken(token);
    try (Connection conn = dataSource.getConnection();
        PreparedStatement check = conn.prepareStatement("SELECT 1 FROM artist_profile WHERE id = ?")) {
      check.setString(1, accountId);
      try (ResultSet rs = check.executeQuery()) {
        if (!rs.next()) {
          try (PreparedStatement ins = conn.prepareStatement(
              "INSERT INTO artist_profile (id, name, image, verified, monthly_listeners, "
                  + "followers, genres, created_at, updated_at) "
                  + "VALUES (?, ?, '/images/placeholder.jpg', false, 0, 0, '{}', now(), now())")) {
            ins.setString(1, accountId);
            ins.setString(2, name);
            ins.executeUpdate();
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to seed artist_profile for IT", e);
    }
  }

  private void seedReadyTrackForArtist(String trackId, String artistId) {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ins = conn.prepareStatement(
            "INSERT INTO track (id, title, artist_id, artist_name, duration_sec, image, "
                + "ownership, price_minor, plays, status) "
                + "VALUES (?, ?, ?, 'Artist', 180, '/images/placeholder.jpg', 'for-sale', 500, 0, "
                + "'uploading') ON CONFLICT (id) DO NOTHING")) {
      ins.setString(1, trackId);
      ins.setString(2, trackId);
      ins.setString(3, artistId);
      ins.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException("Failed to seed track for IT", e);
    }
  }

  @Transactional
  void seedInReviewReleaseWithTrack(
      String releaseId, String artistId, String title, String trackId) {
    em.createNativeQuery(
            "INSERT INTO release (id, artist_id, title, type, status, visibility,"
                + " list_price_minor, created_at, updated_at)"
                + " VALUES (:id, :artistId, :title, 'single', 'in_review', 'public',"
                + " 500, now(), now())")
        .setParameter("id", releaseId)
        .setParameter("artistId", artistId)
        .setParameter("title", title)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO release_track (release_id, track_id, position, price_minor)"
                + " VALUES (:releaseId, :trackId, 0, 500)")
        .setParameter("releaseId", releaseId)
        .setParameter("trackId", trackId)
        .executeUpdate();
  }

  private String accountIdFromToken(String token) {
    String payload = token.split("\\.")[1];
    String json = new String(Base64.getUrlDecoder().decode(payload));
    return json.replaceAll(".*\"sub\"\\s*:\\s*\"([^\"]+)\".*", "$1");
  }
}
