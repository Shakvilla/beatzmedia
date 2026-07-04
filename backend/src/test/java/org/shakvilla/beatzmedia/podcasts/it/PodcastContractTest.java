package org.shakvilla.beatzmedia.podcasts.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Contract conformance test: validates {@code PodcastDto} / {@code PodcastEpisodeDto} /
 * {@code StreamUrlResponse} / the uniform error envelope against {@code API-CONTRACT.md} §8 and
 * {@code Frontend/src/types/index.ts} ({@code Podcast}, {@code PodcastEpisode}). Podcasts ADD
 * §6 / §11.
 *
 * <ul>
 *   <li>{@code Podcast}: {@code id, title, publisher, image, category}, money
 *       {@code seasonPassPrice: { amount, currency }}.
 *   <li>{@code PodcastEpisode}: {@code id, podcastId, title, showTitle, image}, {@code duration}
 *       (whole seconds), {@code publishedAt} (ISO-8601), money {@code price}, boolean flags.
 *   <li>{@code StreamUrlResponse}: {@code audioUrl, expiresAt} (ISO-8601), {@code previewSeconds}
 *       present ONLY when gated (value {@code 30}) — absent (not present as a key) for FULL.
 *   <li>Unknown resource → the uniform error envelope {@code { error: { code, message } } }.
 * </ul>
 */
@QuarkusTest
@Tag("integration")
class PodcastContractTest {

  private static final String ISO_8601_PATTERN =
      "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

  @Inject EntityManager em;

  private String showId;
  private String freeEpisodeId;
  private String premiumEpisodeId;

  @BeforeEach
  @Transactional
  void seed() {
    long n = System.nanoTime();
    showId = "pod-c-show-" + n;
    freeEpisodeId = "pod-c-free-" + n;
    premiumEpisodeId = "pod-c-premium-" + n;

    em.createNativeQuery(
            "INSERT INTO podcast (id, title, publisher, image, category, description,"
                + " episode_count, popularity, season_pass_price_minor, season_pass_currency,"
                + " supports_tips)"
                + " VALUES (:id, 'Contract Show', 'Contract Publisher', 'img.png', 'Tech', 'desc',"
                + " 2, 40, 1000, 'GHS', true)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", showId)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO podcast_episode (id, podcast_id, title, image, duration_sec,"
                + " episode_number, is_premium, is_early_access, published_at)"
                + " VALUES (:id, :showId, 'Contract Free Episode', 'img.png', 1200, 1, false,"
                + " false, now())"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", freeEpisodeId)
        .setParameter("showId", showId)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO podcast_episode (id, podcast_id, title, image, duration_sec,"
                + " episode_number, is_premium, price_minor, price_currency, is_early_access,"
                + " published_at)"
                + " VALUES (:id, :showId, 'Contract Premium Episode', 'img.png', 1800, 2, true,"
                + " 300, 'GHS', false, now())"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", premiumEpisodeId)
        .setParameter("showId", showId)
        .executeUpdate();

    seedReadyMediaAsset(freeEpisodeId);
    seedReadyMediaAsset(premiumEpisodeId);
  }

  private void seedReadyMediaAsset(String episodeId) {
    String assetId = "asset-" + episodeId;
    em.createNativeQuery(
            "INSERT INTO media_asset (id, owner_ref, kind, status, duration_sec, original_key,"
                + " hls_key, preview_key, content_hash, created_at)"
                + " VALUES (:id, :ownerRef, 'AUDIO', 'READY', 1200, :originalKey, :hlsKey,"
                + " :previewKey, :hash, now())"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", assetId)
        .setParameter("ownerRef", "podcasts:" + episodeId)
        .setParameter("originalKey", "beatz-media-originals|originals/audio/" + assetId)
        .setParameter("hlsKey", "beatz-media-delivery|delivery/" + assetId + "/hls/playlist.m3u8")
        .setParameter(
            "previewKey", "beatz-media-delivery|delivery/" + assetId + "/preview/preview.m3u8")
        .setParameter("hash", "hash-" + assetId)
        .executeUpdate();
  }

  // ---- Page<PodcastDto> ----------------------------------------------------------------------

  @Test
  void listPodcasts_matchesPageEnvelope_andPodcastShape() {
    given()
        .when()
        .get("/v1/podcasts")
        .then()
        .statusCode(200)
        .body("items", notNullValue())
        .body("page", isA(Integer.class))
        .body("size", isA(Integer.class))
        .body("total", isA(Integer.class));
  }

  // ---- PodcastDto ------------------------------------------------------------------------------

  @Test
  void getPodcast_matchesPodcastDtoShape_withMoneySeasonPassPrice() {
    given()
        .when()
        .get("/v1/podcasts/" + showId)
        .then()
        .statusCode(200)
        .body("id", equalTo(showId))
        .body("title", isA(String.class))
        .body("publisher", isA(String.class))
        .body("image", isA(String.class))
        .body("category", equalTo("Tech"))
        .body("seasonPassPrice.amount", isA(Float.class))
        .body("seasonPassPrice.currency", equalTo("GHS"))
        .body("supportsTips", equalTo(true));
  }

  // ---- PodcastEpisodeDto ------------------------------------------------------------------------

  @Test
  void listEpisodes_freeEpisode_matchesEpisodeDtoShape_wholeSecondDuration_isoPublishedAt() {
    given()
        .when()
        .get("/v1/podcasts/" + showId + "/episodes")
        .then()
        .statusCode(200)
        .body("find { it.id == '" + freeEpisodeId + "' }.podcastId", equalTo(showId))
        .body("find { it.id == '" + freeEpisodeId + "' }.showTitle", equalTo("Contract Show"))
        .body("find { it.id == '" + freeEpisodeId + "' }.duration", equalTo(1200))
        .body(
            "find { it.id == '" + freeEpisodeId + "' }.publishedAt",
            matchesPattern(ISO_8601_PATTERN));
  }

  @Test
  void listEpisodes_premiumEpisode_hasMoneyPrice_andPremiumFlag() {
    given()
        .when()
        .get("/v1/podcasts/" + showId + "/episodes")
        .then()
        .statusCode(200)
        .body("find { it.id == '" + premiumEpisodeId + "' }.isPremium", equalTo(true))
        .body("find { it.id == '" + premiumEpisodeId + "' }.price.amount", isA(Float.class))
        .body("find { it.id == '" + premiumEpisodeId + "' }.price.currency", equalTo("GHS"));
  }

  // ---- StreamUrlResponse ------------------------------------------------------------------------

  @Test
  void streamResponse_fullRendition_hasRequiredFields_previewSecondsAbsent() {
    given()
        .when()
        .get("/v1/podcasts/episodes/" + freeEpisodeId + "/stream")
        .then()
        .statusCode(200)
        .body("audioUrl", isA(String.class))
        .body("expiresAt", matchesPattern(ISO_8601_PATTERN))
        .body("$", not(hasKey("previewSeconds")));
  }

  @Test
  void streamResponse_previewRendition_hasPreviewSecondsEqual30() {
    given()
        .when()
        .get("/v1/podcasts/episodes/" + premiumEpisodeId + "/stream")
        .then()
        .statusCode(200)
        .body("audioUrl", isA(String.class))
        .body("previewSeconds", equalTo(30))
        .body("expiresAt", matchesPattern(ISO_8601_PATTERN));
  }

  // ---- Uniform error envelope --------------------------------------------------------------------

  @Test
  void getPodcastUnknown_returnsUniformErrorEnvelope() {
    given()
        .when()
        .get("/v1/podcasts/no-such-show-" + System.nanoTime())
        .then()
        .statusCode(404)
        .body("error.code", equalTo("NOT_FOUND"))
        .body("error.message", notNullValue());
  }

  @Test
  void streamUnknownEpisode_returnsUniformErrorEnvelope() {
    given()
        .when()
        .get("/v1/podcasts/episodes/no-such-episode-" + System.nanoTime() + "/stream")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("NOT_FOUND"))
        .body("error.message", notNullValue());
  }
}
