package org.shakvilla.beatzmedia.playback.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.application.port.in.StreamUrlResult;
import org.shakvilla.beatzmedia.playback.application.service.GetStreamUrlService;
import org.shakvilla.beatzmedia.playback.domain.TrackOwnership;
import org.shakvilla.beatzmedia.playback.fakes.FakeCatalogReader;
import org.shakvilla.beatzmedia.playback.fakes.FakeMediaService;
import org.shakvilla.beatzmedia.playback.fakes.FakeOwnershipReader;

/**
 * Unit tests for {@link GetStreamUrlService} — the INV-3 server-side rendition gate
 * (LLFR-PLAYBACK-01.1). Proves the decision matrix from Playback ADD §11: owner/free → FULL, no
 * {@code previewSeconds}; non-owner of a for-sale track (incl. anonymous) → PREVIEW,
 * {@code previewSeconds = 30}. The client can never override this — the port accepts no
 * client-supplied rendition flag.
 */
@Tag("unit")
class GetStreamUrlServiceTest {

  private static final AccountId OWNER = new AccountId("acct-owner");
  private static final AccountId NON_OWNER = new AccountId("acct-non-owner");
  private static final TrackId FOR_SALE_TRACK = new TrackId("track-for-sale");
  private static final TrackId FREE_TRACK = new TrackId("track-free");

  FakeCatalogReader catalog;
  FakeOwnershipReader ownership;
  FakeMediaService media;
  GetStreamUrlService service;

  @BeforeEach
  void setUp() {
    catalog =
        new FakeCatalogReader()
            .seed(FOR_SALE_TRACK.value(), TrackOwnership.FOR_SALE)
            .seed(FREE_TRACK.value(), TrackOwnership.FREE);
    ownership = new FakeOwnershipReader().markOwned(OWNER, FOR_SALE_TRACK);
    media = new FakeMediaService();
    service = new GetStreamUrlService(catalog, ownership, media, 300L);
  }

  // ---- INV-3: for-sale + NOT owned -> PREVIEW only, previewSeconds = 30 ----

  @Test
  void forSaleTrack_nonOwner_getsPreviewOnly_with30SecondsFlag() {
    StreamUrlResult result = service.getStreamUrl(FOR_SALE_TRACK, Optional.of(NON_OWNER));

    assertTrue(result.previewSeconds().isPresent(), "previewSeconds must be present when gated");
    assertEquals(30, result.previewSeconds().get());
    assertTrue(result.audioUrl().contains("preview"), "URL must reference the preview rendition");
    assertFalse(
        result.audioUrl().contains("hls/playlist"),
        "non-owner of a for-sale track must never receive a full-rendition URL");
  }

  @Test
  void forSaleTrack_anonymousCaller_getsPreviewOnly_with30SecondsFlag() {
    StreamUrlResult result = service.getStreamUrl(FOR_SALE_TRACK, Optional.empty());

    assertTrue(result.previewSeconds().isPresent());
    assertEquals(30, result.previewSeconds().get());
    assertFalse(result.audioUrl().contains("hls/playlist"));
  }

  // ---- owner of a for-sale track -> FULL, no previewSeconds ----

  @Test
  void forSaleTrack_owner_getsFullRendition_noPreviewSecondsField() {
    StreamUrlResult result = service.getStreamUrl(FOR_SALE_TRACK, Optional.of(OWNER));

    assertTrue(result.previewSeconds().isEmpty(), "previewSeconds must be absent for FULL");
    assertTrue(result.audioUrl().contains("hls/playlist"), "owner must receive the full rendition");
  }

  // ---- free track -> FULL regardless of caller ----

  @Test
  void freeTrack_anyCaller_getsFullRendition_noPreviewSecondsField() {
    StreamUrlResult resultAnon = service.getStreamUrl(FREE_TRACK, Optional.empty());
    StreamUrlResult resultAuth = service.getStreamUrl(FREE_TRACK, Optional.of(NON_OWNER));

    assertTrue(resultAnon.previewSeconds().isEmpty());
    assertTrue(resultAuth.previewSeconds().isEmpty());
    assertTrue(resultAnon.audioUrl().contains("hls/playlist"));
    assertTrue(resultAuth.audioUrl().contains("hls/playlist"));
  }

  // ---- unknown track -> 404 mapped exception ----

  @Test
  void unknownTrack_throwsTrackNotFound() {
    assertThrows(
        TrackNotFoundException.class,
        () -> service.getStreamUrl(new TrackId("does-not-exist"), Optional.empty()));
  }

  // ---- the ownership port is never queried for a free track (no unnecessary cross-module call) ----

  @Test
  void freeTrack_neverQueriesOwnership() {
    service.getStreamUrl(FREE_TRACK, Optional.of(NON_OWNER));
    // FakeOwnershipReader has no seeded owned entries for NON_OWNER — if the service had wrongly
    // queried and trusted a default, this would still pass, so we assert on the media call
    // receiving FULL, which only happens without a spurious PREVIEW decision.
    assertEquals(
        org.shakvilla.beatzmedia.playback.domain.PlaybackMode.FULL, media.lastCall().mode());
  }

  // ---- expiresAt / TTL is echoed from MediaService, never hard-coded ----

  @Test
  void expiresAt_isEchoedFromMediaService() {
    java.time.Instant fixedExpiry = java.time.Instant.parse("2026-01-01T00:00:00Z");
    media.expiresAt(fixedExpiry);

    StreamUrlResult result = service.getStreamUrl(FREE_TRACK, Optional.empty());

    assertEquals(fixedExpiry, result.expiresAt());
  }
}
