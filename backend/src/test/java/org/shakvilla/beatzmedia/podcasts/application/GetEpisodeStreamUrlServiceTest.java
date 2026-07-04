package org.shakvilla.beatzmedia.podcasts.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.podcasts.application.port.in.StreamUrlResult;
import org.shakvilla.beatzmedia.podcasts.application.service.GetEpisodeStreamUrlService;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeNotFoundException;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastEpisode;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.podcasts.fakes.FakeMediaService;
import org.shakvilla.beatzmedia.podcasts.fakes.FakeOwnershipReader;
import org.shakvilla.beatzmedia.podcasts.fakes.FakePodcastRepository;

/**
 * Unit tests for {@link GetEpisodeStreamUrlService} — the INV-3 server-side rendition gate for
 * podcast episodes (LLFR-PODCAST-01.3), mirroring playback's {@code GetStreamUrlServiceTest}
 * (WU-PLY-1). Proves: owner/free → FULL, no {@code previewSeconds}; non-owner of a
 * premium/early-access episode (incl. anonymous) → PREVIEW, {@code previewSeconds = 30}. The
 * client can never override this — the port accepts no client-supplied rendition flag.
 */
@Tag("unit")
class GetEpisodeStreamUrlServiceTest {

  private static final AccountId OWNER = new AccountId("acct-owner");
  private static final AccountId NON_OWNER = new AccountId("acct-non-owner");
  private static final PodcastId SHOW = new PodcastId("show-1");
  private static final EpisodeId PREMIUM_EPISODE = new EpisodeId("ep-premium");
  private static final EpisodeId FREE_EPISODE = new EpisodeId("ep-free");
  private static final EpisodeId EARLY_ACCESS_EPISODE = new EpisodeId("ep-early");

  private static final Instant PUBLISHED = Instant.parse("2026-06-01T00:00:00Z");
  private static final Instant PUBLIC_AT = Instant.parse("2026-06-10T00:00:00Z");

  FakePodcastRepository repository;
  FakeOwnershipReader ownership;
  FakeMediaService media;
  FakeClock clock;
  GetEpisodeStreamUrlService service;

  @BeforeEach
  void setUp() {
    repository =
        new FakePodcastRepository()
            .withEpisode(
                new PodcastEpisode(
                    PREMIUM_EPISODE,
                    SHOW,
                    "Premium episode",
                    "img.png",
                    null,
                    1800,
                    null,
                    true,
                    Money.ofMinor(300, Currency.GHS),
                    false,
                    null,
                    "asset-premium",
                    PUBLISHED,
                    PUBLISHED))
            .withEpisode(
                new PodcastEpisode(
                    FREE_EPISODE,
                    SHOW,
                    "Free episode",
                    "img.png",
                    null,
                    1200,
                    null,
                    false,
                    null,
                    false,
                    null,
                    "asset-free",
                    PUBLISHED,
                    PUBLISHED))
            .withEpisode(
                new PodcastEpisode(
                    EARLY_ACCESS_EPISODE,
                    SHOW,
                    "Early-access episode",
                    "img.png",
                    null,
                    1500,
                    null,
                    false,
                    Money.ofMinor(500, Currency.GHS),
                    true,
                    PUBLIC_AT,
                    "asset-early",
                    PUBLISHED,
                    PUBLISHED));
    ownership = new FakeOwnershipReader().markOwned(OWNER, PREMIUM_EPISODE).markOwned(OWNER, EARLY_ACCESS_EPISODE);
    media = new FakeMediaService();
    clock = FakeClock.at("2026-06-05T00:00:00Z"); // before PUBLIC_AT
    service = new GetEpisodeStreamUrlService(repository, ownership, media, clock, 300L);
  }

  // ---- INV-3: premium/for-sale + NOT owned -> PREVIEW only, previewSeconds = 30 ----

  @Test
  void premiumEpisode_nonOwner_getsPreviewOnly_with30SecondsFlag() {
    StreamUrlResult result = service.getStreamUrl(PREMIUM_EPISODE, Optional.of(NON_OWNER));

    assertTrue(result.previewSeconds().isPresent(), "previewSeconds must be present when gated");
    assertEquals(30, result.previewSeconds().get());
    assertTrue(result.audioUrl().contains("preview"), "URL must reference the preview rendition");
    assertFalse(
        result.audioUrl().contains("hls/playlist"),
        "non-owner of a premium episode must never receive a full-rendition URL");
  }

  @Test
  void premiumEpisode_anonymousCaller_getsPreviewOnly_with30SecondsFlag() {
    StreamUrlResult result = service.getStreamUrl(PREMIUM_EPISODE, Optional.empty());

    assertTrue(result.previewSeconds().isPresent());
    assertEquals(30, result.previewSeconds().get());
    assertFalse(result.audioUrl().contains("hls/playlist"));
  }

  // ---- owner of a premium episode -> FULL, no previewSeconds ----

  @Test
  void premiumEpisode_owner_getsFullRendition_noPreviewSecondsField() {
    StreamUrlResult result = service.getStreamUrl(PREMIUM_EPISODE, Optional.of(OWNER));

    assertTrue(result.previewSeconds().isEmpty(), "previewSeconds must be absent for FULL");
    assertTrue(result.audioUrl().contains("hls/playlist"), "owner must receive the full rendition");
  }

  // ---- free episode -> FULL regardless of caller ----

  @Test
  void freeEpisode_anyCaller_getsFullRendition_noPreviewSecondsField() {
    StreamUrlResult resultAnon = service.getStreamUrl(FREE_EPISODE, Optional.empty());
    StreamUrlResult resultAuth = service.getStreamUrl(FREE_EPISODE, Optional.of(NON_OWNER));

    assertTrue(resultAnon.previewSeconds().isEmpty());
    assertTrue(resultAuth.previewSeconds().isEmpty());
    assertTrue(resultAnon.audioUrl().contains("hls/playlist"));
    assertTrue(resultAuth.audioUrl().contains("hls/playlist"));
  }

  // ---- early-access: locked (preview) before publicAt unless owned; free to everyone after ----

  @Test
  void earlyAccessEpisode_beforePublicAt_nonOwner_getsPreviewOnly() {
    StreamUrlResult result = service.getStreamUrl(EARLY_ACCESS_EPISODE, Optional.of(NON_OWNER));

    assertTrue(result.previewSeconds().isPresent());
    assertEquals(30, result.previewSeconds().get());
    assertFalse(result.audioUrl().contains("hls/playlist"));
  }

  @Test
  void earlyAccessEpisode_beforePublicAt_owner_getsFullRendition() {
    StreamUrlResult result = service.getStreamUrl(EARLY_ACCESS_EPISODE, Optional.of(OWNER));

    assertTrue(result.previewSeconds().isEmpty());
    assertTrue(result.audioUrl().contains("hls/playlist"));
  }

  @Test
  void earlyAccessEpisode_atOrAfterPublicAt_nonOwner_becomesFreeToEveryone() {
    clock.setNow(PUBLIC_AT); // now >= publicAt

    StreamUrlResult result = service.getStreamUrl(EARLY_ACCESS_EPISODE, Optional.of(NON_OWNER));

    assertTrue(result.previewSeconds().isEmpty());
    assertTrue(result.audioUrl().contains("hls/playlist"));
  }

  @Test
  void earlyAccessEpisode_afterPublicAt_anonymousCaller_becomesFreeToEveryone() {
    clock.setNow(PUBLIC_AT.plusSeconds(3600));

    StreamUrlResult result = service.getStreamUrl(EARLY_ACCESS_EPISODE, Optional.empty());

    assertTrue(result.previewSeconds().isEmpty());
    assertTrue(result.audioUrl().contains("hls/playlist"));
  }

  // ---- unknown episode -> 404 mapped exception ----

  @Test
  void unknownEpisode_throwsEpisodeNotFound() {
    assertThrows(
        EpisodeNotFoundException.class,
        () -> service.getStreamUrl(new EpisodeId("does-not-exist"), Optional.empty()));
  }

  // ---- the ownership port is never queried for a free episode (no unnecessary cross-module call) ----

  @Test
  void freeEpisode_neverQueriesOwnership() {
    service.getStreamUrl(FREE_EPISODE, Optional.of(NON_OWNER));
    assertEquals(0, ownership.ownsEpisodeCalls());
  }

  // ---- expiresAt / TTL is echoed from MediaService, never hard-coded ----

  @Test
  void expiresAt_isEchoedFromMediaService() {
    Instant fixedExpiry = Instant.parse("2026-01-01T00:00:00Z");
    media.expiresAt(fixedExpiry);

    StreamUrlResult result = service.getStreamUrl(FREE_EPISODE, Optional.empty());

    assertEquals(fixedExpiry, result.expiresAt());
  }
}
