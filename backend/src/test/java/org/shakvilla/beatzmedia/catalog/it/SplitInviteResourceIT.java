package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.SplitConfirmation;
import org.shakvilla.beatzmedia.catalog.domain.SplitEntry;
import org.shakvilla.beatzmedia.catalog.domain.SplitInvite;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;

/**
 * Integration tests for {@link
 * org.shakvilla.beatzmedia.catalog.adapter.in.rest.SplitInviteResource} and the {@code
 * resend-invites} endpoint on {@link
 * org.shakvilla.beatzmedia.catalog.adapter.in.rest.StudioReleaseResource} (WU-CAT-9, Task 4).
 *
 * <p>The plaintext invite token is never returned by any API (only its SHA-256 hash is
 * persisted), so this IT hand-inserts a {@link SplitInvite} row via the injected {@link
 * CatalogRepository} with {@code tokenHash = sha256Hex("tok-1")}, linked to a seeded release with
 * a pending split for {@code bob@x.com}, then exercises the public endpoints with the plaintext
 * {@code "tok-1"}. Test JWTs are minted directly with {@code io.smallrye.jwt.build.Jwt} (mirrors
 * {@code JwtTokenIssuer}), signed with the same dev keypair the app verifies against.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SplitInviteResourceIT {

  private static final String INVITES_URL = "/v1/splits/invites";
  private static final String RELEASES_URL = "/v1/studio/releases";

  // Reuses the dev-seeded 'black-sherif' artist_profile row for the release.artist_id FK (same
  // pattern as SplitInvitePersistenceIT / SplitPersistenceIT).
  private static final String OWNER_ARTIST_ID = "black-sherif";
  private static final String OTHER_ARTIST_ID = "not-the-owner-artist";

  private static final String RELEASE_ID = "rel-splitinvite-it-1";
  private static final String TRACK_ID = "trk-splitinvite-it-1";
  private static final String TRACK_TITLE = TRACK_ID + " title";
  private static final String BOB_EMAIL = "bob@x.com";
  private static final String BOB_ACCOUNT_ID = "acc-bob";
  private static final String PLAINTEXT_TOKEN = "tok-1";

  private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");

  @Inject
  CatalogRepository repo;

  @ConfigProperty(name = "mp.jwt.verify.issuer")
  String issuer;

  private static String ownerToken;
  private static String otherArtistToken;
  private static String bobToken;

  // ============================
  // Setup: seed release + track + pending split + invite; mint test JWTs
  // ============================

  @Test
  @Order(1)
  @Transactional
  void setup_seedReleaseAndInvite() {
    repo.saveTrack(new Track(
        new TrackId(TRACK_ID),
        TRACK_TITLE,
        new ArtistId(OWNER_ARTIST_ID),
        null,
        null,
        null,
        200,
        "/images/placeholder.jpg",
        OwnershipStatus.free,
        null,
        0L,
        null,
        null,
        null,
        null,
        "ready"));

    Release release = Release.reconstitute(
        RELEASE_ID, OWNER_ARTIST_ID, "Split Invite IT Release", ReleaseType.single,
        org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus.draft,
        Visibility.PUBLIC, null, null, 0L, NOW, NOW,
        List.of(new ReleaseTrack(TRACK_ID, 0, 250L)), null, null, List.of());
    repo.saveRelease(release);

    repo.saveTrackSplits(TRACK_ID, List.of(new SplitEntry(
        "split-splitinvite-it-1", TRACK_ID, "Bob Collaborator", BOB_EMAIL, "Producer", 30,
        SplitConfirmation.pending)));

    SplitInvite invite = SplitInvite.issue(
        "inv-splitinvite-it-1", RELEASE_ID, BOB_EMAIL, sha256Hex(PLAINTEXT_TOKEN),
        NOW.plus(Duration.ofDays(14)), NOW);
    repo.saveSplitInvite(invite);

    ownerToken = mintJwt(OWNER_ARTIST_ID, Set.of("artist"));
    otherArtistToken = mintJwt(OTHER_ARTIST_ID, Set.of("artist"));
    bobToken = mintJwt(BOB_ACCOUNT_ID, Set.of("user"));
  }

  // ============================
  // GET /v1/splits/invites/:token
  // ============================

  @Test
  @Order(2)
  void get_invite_by_token_noAuth_returns200_withTracks() {
    given()
        .when().get(INVITES_URL + "/" + PLAINTEXT_TOKEN)
        .then()
        .statusCode(200)
        .body("status", equalTo("pending"))
        .body("artistName", notNullValue())
        .body("releaseTitle", equalTo("Split Invite IT Release"))
        .body("tracks[0].trackTitle", equalTo(TRACK_TITLE))
        .body("tracks[0].role", equalTo("Producer"))
        .body("tracks[0].percent", equalTo(30));
  }

  @Test
  @Order(3)
  void get_unknown_token_returns404() {
    given()
        .when().get(INVITES_URL + "/nope")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("SPLIT_INVITE_NOT_FOUND"));
  }

  // ============================
  // POST /v1/splits/invites/:token/accept
  // ============================

  @Test
  @Order(4)
  void accept_withoutJwt_returns401() {
    given()
        .when().post(INVITES_URL + "/" + PLAINTEXT_TOKEN + "/accept")
        .then()
        .statusCode(401);
  }

  @Test
  @Order(5)
  void accept_withBobJwt_returns204_confirmsSplit_consumesInvite() {
    given()
        .header("Authorization", "Bearer " + bobToken)
        .when().post(INVITES_URL + "/" + PLAINTEXT_TOKEN + "/accept")
        .then()
        .statusCode(204);

    Release read = repo.findRelease(new ReleaseId(RELEASE_ID)).orElseThrow();
    SplitEntry bobSplit = read.getSplits().stream()
        .filter(s -> s.email().equals(BOB_EMAIL)).findFirst().orElseThrow();
    assertEquals(SplitConfirmation.confirmed, bobSplit.confirmation());
    assertEquals(BOB_ACCOUNT_ID, bobSplit.accountId());

    SplitInvite invite = repo.findSplitInviteByHash(sha256Hex(PLAINTEXT_TOKEN)).orElseThrow();
    assertTrue(invite.isConsumed());
  }

  @Test
  @Order(6)
  void accept_again_returns410() {
    given()
        .header("Authorization", "Bearer " + bobToken)
        .when().post(INVITES_URL + "/" + PLAINTEXT_TOKEN + "/accept")
        .then()
        .statusCode(410)
        .body("error.code", equalTo("SPLIT_INVITE_GONE"));
  }

  // ============================
  // POST /v1/studio/releases/:id/resend-invites
  // ============================

  @Test
  @Order(7)
  void resendInvites_withOwningArtistJwt_returns204() {
    given()
        .header("Authorization", "Bearer " + ownerToken)
        .when().post(RELEASES_URL + "/" + RELEASE_ID + "/resend-invites")
        .then()
        .statusCode(204);
  }

  @Test
  @Order(8)
  void resendInvites_withNonOwnerArtistJwt_returns403() {
    given()
        .header("Authorization", "Bearer " + otherArtistToken)
        .when().post(RELEASES_URL + "/" + RELEASE_ID + "/resend-invites")
        .then()
        .statusCode(403);
  }

  // ============================
  // Helpers
  // ============================

  private String mintJwt(String subject, Set<String> roles) {
    // Real wall-clock time, NOT the fixed NOW used for domain fixtures — the server verifies
    // exp/iat against the actual system clock.
    Instant now = Instant.now();
    return Jwt.issuer(issuer)
        .subject(subject)
        .groups(roles)
        .issuedAt(now.getEpochSecond())
        .expiresAt(now.plusSeconds(900).getEpochSecond())
        .sign();
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
