package org.shakvilla.beatzmedia.playback.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.application.port.in.DownloadUrlResult;
import org.shakvilla.beatzmedia.playback.application.service.GetDownloadUrlService;
import org.shakvilla.beatzmedia.playback.domain.DownloadNotAllowedException;
import org.shakvilla.beatzmedia.playback.domain.DownloadNotReadyException;
import org.shakvilla.beatzmedia.playback.domain.NotOwnedException;
import org.shakvilla.beatzmedia.playback.domain.TrackOwnership;
import org.shakvilla.beatzmedia.playback.fakes.FakeCatalogReader;
import org.shakvilla.beatzmedia.playback.fakes.FakeDownloadPermissionReader;
import org.shakvilla.beatzmedia.playback.fakes.FakeMediaService;
import org.shakvilla.beatzmedia.playback.fakes.FakeOwnershipReader;

/**
 * The download guards, each asserted separately so a failure names which one broke. Guard order is
 * load-bearing and is itself asserted: track exists (404) → owned (403) → the grant permits (409
 * DOWNLOAD_NOT_ALLOWED) → the FLAC exists (409 DOWNLOAD_NOT_READY).
 *
 * <p>{@link #theGrantIsTheAuthorityNotTheRelease()} guards the design's most fragile decision:
 * answering from {@code release.downloadable} here would retract downloads from people who already
 * paid, the moment an artist changed their mind. At this layer the guarantee is structural — the
 * service's only permission collaborator is {@link
 * org.shakvilla.beatzmedia.playback.application.port.out.DownloadPermissionReader}, which is the
 * caller's own grant, and no collaborator here can even see the release's current setting. The
 * behavioural half of the same guard (a grant outliving an artist's change of mind) lives in
 * commerce's {@code GetTrackDownloadPermissionServiceTest}, where the release IS reachable and the
 * wrong implementation would compile.
 */
@Tag("unit")
class GetDownloadUrlServiceTest {

  private static final AccountId OWNER = new AccountId("acct-1");
  private static final AccountId NON_OWNER = new AccountId("acct-2");
  private static final TrackId TRACK = new TrackId("t1");

  private static final long TTL_SECONDS = 300L;

  FakeCatalogReader catalog;
  FakeOwnershipReader ownership;
  FakeDownloadPermissionReader permission;
  FakeMediaService media;
  GetDownloadUrlService service;

  @BeforeEach
  void setUp() {
    catalog = new FakeCatalogReader().seed(TRACK.value(), TrackOwnership.FOR_SALE);
    ownership = new FakeOwnershipReader();
    permission = new FakeDownloadPermissionReader();
    media = new FakeMediaService();
    service = new GetDownloadUrlService(catalog, ownership, permission, media, TTL_SECONDS);
  }

  /** The caller owns the track and the grant they hold permits downloading. */
  private void owns(AccountId account, TrackId track, boolean grantPermitsDownload) {
    ownership.markOwned(account, track);
    if (grantPermitsDownload) {
      permission.permit(account, track);
    }
  }

  // ---- happy path ----

  @Test
  void anOwnerWithPermissionGetsALosslessUrl() {
    owns(OWNER, TRACK, true);
    media.hasLossless(TRACK.value());

    DownloadUrlResult r = service.getDownloadUrl(TRACK, OWNER);

    assertEquals("flac", r.format());
    assertTrue(
        r.downloadUrl().contains("lossless.flac"),
        "the download must be the lossless rendition, got: " + r.downloadUrl());
    assertFalse(
        r.downloadUrl().contains("full.m4a"),
        "a download must never hand back the lossy streaming rendition");
  }

  @Test
  void theSignedUrlTtlComesFromConfigurationNotALiteral() {
    owns(OWNER, TRACK, true);
    media.hasLossless(TRACK.value());
    GetDownloadUrlService configured =
        new GetDownloadUrlService(catalog, ownership, permission, media, 900L);

    configured.getDownloadUrl(TRACK, OWNER);

    assertEquals(Duration.ofSeconds(900L), media.losslessCalls().get(0).ttl());
  }

  @Test
  void expiresAtIsEchoedFromMediaServiceNotComputedLocally() {
    Instant fixedExpiry = Instant.parse("2026-01-01T00:00:00Z");
    media.expiresAt(fixedExpiry).hasLossless(TRACK.value());
    owns(OWNER, TRACK, true);

    assertEquals(fixedExpiry, service.getDownloadUrl(TRACK, OWNER).expiresAt());
  }

  // ---- the four refusals ----

  @Test
  void anUnknownTrackIsNotFound() {
    owns(OWNER, new TrackId("does-not-exist"), true);

    assertThrows(
        TrackNotFoundException.class,
        () -> service.getDownloadUrl(new TrackId("does-not-exist"), OWNER));
    assertEquals(
        List.of(),
        permission.queries(),
        "existence is checked before permission — no cross-module call for a track that isn't there");
  }

  @Test
  void aNonOwnerIsRefused() {
    // Not owned. Nothing else is seeded: no grant, no permission.
    assertThrows(NotOwnedException.class, () -> service.getDownloadUrl(TRACK, NON_OWNER));
  }

  @Test
  void anOwnerWhoseGrantForbidsDownloadIsRefused() {
    owns(OWNER, TRACK, false);
    media.hasLossless(TRACK.value());

    assertThrows(DownloadNotAllowedException.class, () -> service.getDownloadUrl(TRACK, OWNER));
  }

  @Test
  void anAssetWithNoLosslessRenditionIsRefusedRatherThanServedTheAac() {
    owns(OWNER, TRACK, true);
    // no media.hasLossless(...) — the FLAC has not been produced

    assertThrows(DownloadNotReadyException.class, () -> service.getDownloadUrl(TRACK, OWNER));
  }

  // ---- guard ORDER, which is what keeps the refusals from leaking ----

  @Test
  void aNonOwnerIsToldNotOwnedRatherThanNotReady() {
    // The FLAC does not exist either. A non-owner must not learn that: ownership is checked first,
    // so the answer a stranger gets never varies with the asset's transcode state.
    assertThrows(NotOwnedException.class, () -> service.getDownloadUrl(TRACK, NON_OWNER));
  }

  @Test
  void aNonOwnerNeverReachesTheRenditionCheck() {
    assertThrows(NotOwnedException.class, () -> service.getDownloadUrl(TRACK, NON_OWNER));

    assertEquals(
        List.of(), media.losslessCalls(), "a non-owner must never cause a signing round-trip");
  }

  @Test
  void anOwnerWithoutPermissionNeverReachesTheRenditionCheck() {
    owns(OWNER, TRACK, false);
    media.hasLossless(TRACK.value());

    assertThrows(DownloadNotAllowedException.class, () -> service.getDownloadUrl(TRACK, OWNER));

    assertEquals(List.of(), media.losslessCalls(), "no URL may be minted for a refused download");
  }

  // ---- THE rule ----

  @Test
  void theGrantIsTheAuthorityNotTheRelease() {
    // The grant says yes. The release's current setting is irrelevant and, by construction, not
    // even reachable from this service — its only collaborators are catalog existence, ownership,
    // the grant's permission, and media. The artist may since have said no; this must still work.
    owns(OWNER, TRACK, true);
    media.hasLossless(TRACK.value());

    assertEquals("flac", service.getDownloadUrl(TRACK, OWNER).format());
    assertEquals(
        List.of(permission.key(OWNER, TRACK)),
        permission.queries(),
        "the permission answer must come from the caller's own grant, asked exactly once");
  }
}
