package org.shakvilla.beatzmedia.media.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.application.service.MagicByteValidator;
import org.shakvilla.beatzmedia.media.application.service.MediaApplicationService;
import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.MediaAsset;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.MediaStatus;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.media.domain.SignedUrl;
import org.shakvilla.beatzmedia.media.fakes.FakeArtworkProcessor;
import org.shakvilla.beatzmedia.media.fakes.FakeMediaAssetRepository;
import org.shakvilla.beatzmedia.media.fakes.FakeMediaReadyEvent;
import org.shakvilla.beatzmedia.media.fakes.FakeObjectStore;
import org.shakvilla.beatzmedia.media.fakes.FakeTranscodeJobPort;
import org.shakvilla.beatzmedia.media.fakes.FakeUrlSigner;
import org.shakvilla.beatzmedia.media.fakes.FakeVirusScan;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * INV-3 guard — verifies that the full delivery URL is NEVER produced without an explicit
 * ownership assertion ({@link DeliveryVariant#FULL}). Fulfils ADD §12 DoD §1.
 */
class Inv3PreviewGateTest {

  private FakeMediaAssetRepository repository;
  private FakeUrlSigner urlSigner;
  private MediaApplicationService service;

  @BeforeEach
  void setUp() {
    repository = new FakeMediaAssetRepository();
    urlSigner = new FakeUrlSigner();
    service = new MediaApplicationService(
        repository,
        new FakeObjectStore(),
        urlSigner,
        new FakeTranscodeJobPort(),
        new FakeVirusScan(),
        new FakeArtworkProcessor(),
        new MagicByteValidator(),
        FakeIds.sequential("asset"),
        FakeClock.fixed(),
        new FakeMediaReadyEvent(),
        30);
  }

  /** Seed a READY audio asset into the repository. */
  private MediaAssetId seedReadyAudioAsset() {
    MediaAssetId id = new MediaAssetId("asset-inv3-001");
    ObjectKey originalKey = new ObjectKey("test-originals", "originals/audio/asset-inv3-001");
    // Real delivery key shape (FfmpegAudioTranscoderAdapter, ADR-34). A stale "/hls/playlist.m3u8"
    // fixture made the "never leaks the full rendition" assertions vacuous: they looked for a path
    // segment the production keys can no longer contain, so they passed without discriminating.
    ObjectKey fullKey = new ObjectKey("test-delivery", "delivery/asset-inv3-001/full.m4a");
    ObjectKey previewKey = new ObjectKey("test-delivery", "delivery/asset-inv3-001/preview.m4a");
    MediaAsset asset = new MediaAsset(
        id,
        new OwnerRef("catalog", "track-999"),
        MediaKind.AUDIO,
        MediaStatus.READY,
        185,
        originalKey,
        fullKey,
        previewKey,
        null,
        Instant.parse("2026-01-01T00:00:00Z"),
        "hash-seed");
    repository.save(asset);
    return id;
  }

  /**
   * INV-3: When the caller passes PREVIEW the returned URL must target the preview key (the
   * server-clipped rendition), not the full one. There MUST be no code path that returns the full
   * key for PREVIEW.
   */
  @Test
  void preview_variant_returns_preview_key_url() {
    MediaAssetId id = seedReadyAudioAsset();

    SignedUrl signed = service.issueSignedUrl(id, DeliveryVariant.PREVIEW, Duration.ofSeconds(300));

    assertNotNull(signed.url());
    // The URL must reference preview.m4a, never full.m4a
    assertEquals(
        DeliveryVariant.PREVIEW,
        signed.variant(),
        "Variant in SignedUrl must be PREVIEW");
    assertFakeUrlContainsPreviewKey(signed.url());
  }

  /**
   * INV-3: When the caller explicitly passes FULL (ownership asserted by caller) the URL must
   * target the full key. This is the only path that may produce the full key.
   */
  @Test
  void full_variant_with_ownership_asserted_returns_full_key_url() {
    MediaAssetId id = seedReadyAudioAsset();

    SignedUrl signed = service.issueSignedUrl(id, DeliveryVariant.FULL, Duration.ofSeconds(300));

    assertNotNull(signed.url());
    assertEquals(
        DeliveryVariant.FULL,
        signed.variant(),
        "Variant in SignedUrl must be FULL");
    assertFakeUrlContainsFullKey(signed.url());
  }

  /**
   * INV-3: There is NO code path that presigns fullKey without an asserted ownership decision.
   * Specifically: passing PREVIEW must NEVER produce the full rendition's URL.
   * Symmetrically: passing FULL must NOT produce the preview URL.
   */
  @Test
  void preview_url_never_references_the_full_rendition() {
    MediaAssetId id = seedReadyAudioAsset();
    SignedUrl signed = service.issueSignedUrl(id, DeliveryVariant.PREVIEW, Duration.ofSeconds(300));
    String url = signed.url();
    if (url.contains("full.m4a")) {
      throw new AssertionError(
          "INV-3 VIOLATED: PREVIEW variant produced a URL referencing full.m4a. URL: " + url);
    }
  }

  @Test
  void full_url_never_references_the_preview_rendition() {
    MediaAssetId id = seedReadyAudioAsset();
    SignedUrl signed = service.issueSignedUrl(id, DeliveryVariant.FULL, Duration.ofSeconds(300));
    String url = signed.url();
    if (url.contains("preview.m4a")) {
      throw new AssertionError(
          "Full variant should not produce a preview.m4a URL. URL: " + url);
    }
  }

  @Test
  void signed_url_has_finite_expires_at() {
    MediaAssetId id = seedReadyAudioAsset();
    SignedUrl signed = service.issueSignedUrl(id, DeliveryVariant.PREVIEW, Duration.ofSeconds(300));
    assertNotNull(signed.expiresAt(), "expiresAt must not be null");
  }

  // ---- Helpers ----

  private void assertFakeUrlContainsPreviewKey(String url) {
    if (!url.contains("preview")) {
      throw new AssertionError(
          "Expected URL to reference preview rendition but got: " + url);
    }
  }

  private void assertFakeUrlContainsFullKey(String url) {
    if (!url.contains("full.m4a")) {
      throw new AssertionError(
          "Expected URL to reference the full rendition but got: " + url);
    }
  }
}
