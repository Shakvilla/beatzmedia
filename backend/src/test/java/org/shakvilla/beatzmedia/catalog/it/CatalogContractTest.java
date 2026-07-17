package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Base64;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Contract conformance test: validates that responses match the shapes defined in
 * {@code Frontend/src/types/index.ts} and {@code API-CONTRACT.md}. Field names, money shape
 * {@code {amount, currency}}, duration seconds, ownership enum values, and status codes are
 * asserted. Catalog ADD §11 / testing-strategy §5.
 */
@QuarkusTest
@Tag("integration")
class CatalogContractTest {

  @Inject
  FeatureFlags featureFlags;

  @Inject
  EntityManager em;

  // --- Artist contract: { id, name, image, coverImage?, verified?, monthlyListeners?,
  //                        followers?, bio?, location?, genres? }

  @Test
  void artist_response_has_required_fields() {
    given()
        .when().get("/v1/artists/black-sherif")
        .then()
        .statusCode(200)
        .body("id", isA(String.class))
        .body("name", isA(String.class))
        .body("image", isA(String.class))
        .body("verified", isA(Boolean.class))
        .body("monthlyListeners", notNullValue())
        .body("followers", notNullValue());
  }

  // --- Track contract: { id, title, artistId, artistName, duration (int seconds),
  //                       image, ownership ("owned"|"free"|"for-sale"), price? { amount, currency },
  //                       plays?, audioUrl?, credits?, quality?, year? }

  @Test
  void track_owned_response_omits_price() {
    given()
        .when().get("/v1/tracks/last-last")
        .then()
        .statusCode(200)
        .body("id", equalTo("last-last"))
        .body("artistId", isA(String.class))
        .body("artistName", isA(String.class))
        .body("duration", isA(Integer.class))  // whole seconds
        .body("ownership", equalTo("owned"))
        .body("price", equalTo(null));         // owned → no price field
  }

  @Test
  void track_for_sale_response_has_money_shape() {
    // Money: { amount: decimal cedis, currency: "GHS" }  — INV-11 / API-CONTRACT §1
    given()
        .when().get("/v1/tracks/its-plenty")
        .then()
        .statusCode(200)
        .body("ownership", equalTo("for-sale"))
        .body("price", notNullValue())
        .body("price.amount", isA(Number.class))    // decimal cedis
        .body("price.currency", equalTo("GHS"));    // always GHS
  }

  @Test
  void track_with_credits_includes_credits_array() {
    // last-last has credits in the seed
    given()
        .when().get("/v1/tracks/last-last")
        .then()
        .statusCode(200)
        .body("credits", notNullValue())
        .body("credits[0].role", isA(String.class))
        .body("credits[0].names", notNullValue());
  }

  // --- Album contract: { id, title, artistId, artistName, year, coverImage, genres?, trackIds }

  @Test
  void album_response_has_required_fields() {
    given()
        .when().get("/v1/albums/iron-boy")
        .then()
        .statusCode(200)
        .body("id", equalTo("iron-boy"))
        .body("title", isA(String.class))
        .body("artistId", isA(String.class))
        .body("artistName", isA(String.class))
        .body("year", isA(Integer.class))
        .body("coverImage", isA(String.class))
        .body("trackIds", notNullValue());
  }

  // --- Playlist contract: { id, title, description?, creator, creatorAvatar?,
  //                          image, isPublic, followers?, trackIds, tracks }

  @Test
  void playlist_response_has_required_fields_and_embedded_tracks() {
    given()
        .when().get("/v1/playlists/vibes-from-the-233")
        .then()
        .statusCode(200)
        .body("id", isA(String.class))
        .body("title", isA(String.class))
        .body("creator", isA(String.class))
        .body("image", isA(String.class))
        .body("isPublic", isA(Boolean.class))
        .body("trackIds", notNullValue())
        .body("tracks", notNullValue())
        .body("tracks[0].id", isA(String.class));
  }

  // --- Lyrics contract: { lines: { time: int, text: string }[] }

  @Test
  void lyrics_response_shape_matches_contract() {
    given()
        .when().get("/v1/tracks/last-last/lyrics")
        .then()
        .statusCode(200)
        .body("lines", notNullValue())
        .body("lines[0].time", isA(Integer.class))   // whole seconds
        .body("lines[0].text", isA(String.class));
  }

  // --- Show contract: { date: string, city: string, venue: string }

  @Test
  void show_response_shape_matches_contract() {
    given()
        .when().get("/v1/artists/black-sherif/shows")
        .then()
        .statusCode(200)
        .body("[0].date", isA(String.class))
        .body("[0].city", isA(String.class))
        .body("[0].venue", isA(String.class));
  }

  // --- Batch resolve contract: { tracks: Track[], artists: Artist[], albums: Album[],
  //                                playlists: Playlist[] } — every array present, ids-not-found
  //                                silently omitted (200, not 404).

  @Test
  void resolve_response_has_all_four_arrays_and_matching_item_shapes() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "trackIds": ["last-last"],
              "artistIds": ["black-sherif"],
              "albumIds": ["iron-boy"],
              "playlistIds": ["vibes-from-the-233"]
            }
            """)
        .when().post("/v1/catalog/resolve")
        .then()
        .statusCode(200)
        .body("tracks[0].id", equalTo("last-last"))
        .body("tracks[0].ownership", isA(String.class))
        .body("artists[0].id", equalTo("black-sherif"))
        .body("artists[0].verified", isA(Boolean.class))
        .body("albums[0].id", equalTo("iron-boy"))
        .body("albums[0].year", isA(Integer.class))
        .body("playlists[0].id", equalTo("vibes-from-the-233"))
        .body("playlists[0].tracks[0].id", isA(String.class));
  }

  @Test
  void resolve_unknown_id_omitted_not_404() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "trackIds": ["totally-bogus-id"] }
            """)
        .when().post("/v1/catalog/resolve")
        .then()
        .statusCode(200)
        .body("tracks", org.hamcrest.Matchers.empty());
  }

  // --- Error envelope contract: { error: { code, message } }

  @Test
  void not_found_error_envelope_has_correct_structure() {
    given()
        .when().get("/v1/artists/nobody-xyz")
        .then()
        .statusCode(404)
        .body("error", notNullValue())
        .body("error.code", equalTo("ARTIST_NOT_FOUND"))
        .body("error.message", isA(String.class));
  }

  // --- WU-CAT-4: StudioRelease.status enum + ILLEGAL_TRANSITION error code (LLFR-CATALOG-02.5) ---

  @Test
  void studio_release_status_enum_conforms_to_contract() {
    // API-CONTRACT.md §"Releases": status: live|scheduled|in_review|draft|takedown.
    // WU-CAT-5: POST /v1/studio/releases now creates a metadata-only draft (draft ->
    // upload-attached -> finalize supersedes the old one-shot direct-to-in_review submit).
    String artistToken = provisionArtist();
    Response created = given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Contract Status Release", "type": "single", "visibility": "public" }
            """)
        .when().post("/v1/studio/releases")
        .then().statusCode(201).extract().response();

    created.then().body("status",
        anyOf(equalTo("draft"), equalTo("in_review"), equalTo("scheduled"),
            equalTo("live"), equalTo("takedown")));
    created.then().body("status", equalTo("draft"));
  }

  @Test
  void admin_approve_on_illegal_status_returns_ILLEGAL_TRANSITION_error_code() {
    String artistToken = provisionArtist();
    String moderatorToken = provisionModerator();

    // WU-CAT-5: the release-creation flow is draft -> upload-attached -> finalize. This
    // contract test only needs an in_review release to exercise the admin approve endpoint's
    // error envelope, not the draft-authoring flow itself, so it seeds the release +
    // release_track rows directly rather than driving the full draft flow.
    String releaseId = "contract-illegal-release-" + System.nanoTime();
    seedInReviewReleaseWithTrack(
        releaseId, subjectOf(artistToken), "Contract Illegal Transition Release",
        "contract-illegal-track");

    // Approve once: in_review -> live
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post("/v1/admin/catalog/" + releaseId + "/approve")
        .then().statusCode(200).body("status", equalTo("live"));

    // Approve again: live -> live is illegal
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post("/v1/admin/catalog/" + releaseId + "/approve")
        .then()
        .statusCode(409)
        .body("error", notNullValue())
        .body("error.code", equalTo("ILLEGAL_TRANSITION"))
        .body("error.message", isA(String.class));
  }

  // --- WU-CAT-5: list StudioReleaseView unchanged; GET /:id StudioReleaseDetailView additive ---

  @Test
  void studio_release_list_view_shape_is_byte_for_byte_unchanged() {
    // StudioReleaseView (list): { id, title, type, status, date, trackCount, streams,
    // revenue: {amount,currency}, price: {amount,currency} } — no genre/description/visibility/
    // scheduledAt/tracks (those are additive on the detail view only).
    String artistToken = provisionArtist();
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "List Shape Release", "type": "single", "visibility": "public" }
            """)
        .when().post("/v1/studio/releases")
        .then().statusCode(201);

    given()
        .header("Authorization", "Bearer " + artistToken)
        .queryParam("size", 50)
        .when().get("/v1/studio/releases")
        .then()
        .statusCode(200)
        .body("items[0].id", isA(String.class))
        .body("items[0].title", isA(String.class))
        .body("items[0].type", isA(String.class))
        .body("items[0].status", isA(String.class))
        .body("items[0].date", isA(String.class))
        .body("items[0].trackCount", isA(Integer.class))
        .body("items[0].streams", notNullValue())
        .body("items[0].revenue.amount", isA(Number.class))
        .body("items[0].revenue.currency", equalTo("GHS"))
        .body("items[0].price.amount", isA(Number.class))
        .body("items[0].price.currency", equalTo("GHS"))
        .body("items[0]", not(hasKey("genre")))
        .body("items[0]", not(hasKey("description")))
        .body("items[0]", not(hasKey("visibility")))
        .body("items[0]", not(hasKey("scheduledAt")))
        .body("items[0]", not(hasKey("tracks")));
  }

  @Test
  void studio_release_detail_view_is_additive_with_lowercase_enums_and_track_money_shape() {
    // StudioReleaseDetailView (GET /:id): StudioReleaseView + { genre, description, visibility,
    // scheduledAt, tracks: TrackDraftView[] }. tracks[].price is {amount,currency}; status/type/
    // visibility are lowercase enum wire tokens (API-CONTRACT.md).
    String artistToken = provisionArtist();
    String releaseId = given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "title": "Detail Shape Release", "type": "single", "visibility": "public",
              "genre": "Afrobeats", "description": "Test bio"
            }
            """)
        .when().post("/v1/studio/releases")
        .then().statusCode(201).extract().jsonPath().getString("id");

    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get("/v1/studio/releases/" + releaseId)
        .then()
        .statusCode(200)
        .body("id", equalTo(releaseId))
        .body("status", equalTo("draft"))
        .body("type", equalTo("single"))
        .body("visibility", equalTo("public"))
        .body("genre", equalTo("Afrobeats"))
        .body("description", equalTo("Test bio"))
        .body("price.amount", isA(Number.class))
        .body("price.currency", equalTo("GHS"))
        .body("tracks", org.hamcrest.Matchers.empty());
  }

  // ---- helpers ----

  private String provisionArtist() {
    String email = "contract-artist-" + System.nanoTime() + "@example.com";
    String password = "password123";
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Contract Artist", "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post("/v1/auth/signup")
        .then().statusCode(201);

    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);
    String firstToken = login(email, password);
    given()
        .header("Authorization", "Bearer " + firstToken)
        .when().post("/v1/me/become-artist")
        .then().statusCode(200);

    String artistToken = login(email, password);
    seedArtistProfile(artistToken);
    seedTracksForArtist(artistToken, "contract-status-track", "contract-illegal-track");
    return artistToken;
  }

  private String provisionModerator() {
    String email = "contract-moderator-" + System.nanoTime() + "@example.com";
    String password = "modpassword123";
    Response resp = given()
        .contentType(ContentType.JSON)
        .body("""
            {"name":"Contract Moderator","email":"%s","password":"%s"}
            """.formatted(email, password))
        .post("/v1/auth/signup")
        .then().statusCode(201).extract().response();
    String accountId = resp.jsonPath().getString("account.id");
    promoteToModerator(accountId);
    return login(email, password);
  }

  private String login(String email, String password) {
    return given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private void seedArtistProfile(String token) {
    String accountId = subjectOf(token);
    inTransaction(() -> em.createNativeQuery(
            "INSERT INTO artist_profile (id, name, image, verified, monthly_listeners, "
                + "followers, genres, created_at, updated_at) "
                + "VALUES (:id, 'Contract Artist', '/images/placeholder.jpg', false, 0, 0, "
                + "'{}', now(), now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", accountId)
        .executeUpdate());
  }

  private void seedTracksForArtist(String token, String... trackIds) {
    String artistId = subjectOf(token);
    for (String trackId : trackIds) {
      inTransaction(() -> em.createNativeQuery(
              "INSERT INTO track (id, title, artist_id, artist_name, duration_sec, image, "
                  + "ownership, price_minor, plays, status) "
                  + "VALUES (:id, :id, :artistId, 'Artist', 180, '/images/placeholder.jpg', "
                  + "'for-sale', 500, 0, 'uploading') ON CONFLICT (id) DO NOTHING")
          .setParameter("id", trackId)
          .setParameter("artistId", artistId)
          .executeUpdate());
    }
  }

  @Transactional
  void promoteToModerator(String accountId) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, 'moderator', now())")
        .setParameter("memberId", "contract-moderator-" + accountId)
        .setParameter("accountId", accountId)
        .executeUpdate();
  }

  @Transactional
  void inTransaction(Runnable r) {
    r.run();
  }

  @Transactional
  void seedInReviewReleaseWithTrack(String releaseId, String artistId, String title, String trackId) {
    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, duration_sec, image, "
                + "ownership, price_minor, plays, status) "
                + "VALUES (:id, :id, :artistId, 'Artist', 180, '/images/placeholder.jpg', "
                + "'for-sale', 500, 0, 'uploading') ON CONFLICT (id) DO NOTHING")
        .setParameter("id", trackId)
        .setParameter("artistId", artistId)
        .executeUpdate();
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

  private String subjectOf(String token) {
    String payload = token.split("\\.")[1];
    String json = new String(Base64.getUrlDecoder().decode(payload));
    return json.replaceAll(".*\"sub\"\\s*:\\s*\"([^\"]+)\".*", "$1");
  }
}
