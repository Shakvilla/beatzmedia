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
import org.shakvilla.beatzmedia.media.fakes.FakeMediaAssetRepository;
import org.shakvilla.beatzmedia.media.fakes.FakeMediaReadyEvent;
import org.shakvilla.beatzmedia.media.fakes.FakeObjectStore;
import org.shakvilla.beatzmedia.media.fakes.FakeTranscodeJobPort;
import org.shakvilla.beatzmedia.media.fakes.FakeUrlSigner;
import org.shakvilla.beatzmedia.media.fakes.FakeVirusScan;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * INV-3 guard — verifies that the full HLS delivery URL is NEVER produced without an explicit
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
    ObjectKey hlsKey = new ObjectKey("test-delivery", "delivery/asset-inv3-001/hls/playlist.m3u8");
    ObjectKey previewKey =
        new ObjectKey("test-delivery", "delivery/asset-inv3-001/preview/preview.m3u8");
    MediaAsset asset = new MediaAsset(
        id,
        new OwnerRef("catalog", "track-999"),
        MediaKind.AUDIO,
        MediaStatus.READY,
        185,
        originalKey,
        hlsKey,
        previewKey,
        Instant.parse("2026-01-01T00:00:00Z"),
        "hash-seed");
    repository.save(asset);
    return id;
  }

  /**
   * INV-3: When the caller passes PREVIEW the returned URL must target the preview key (30s clip),
   * not the full HLS playlist. There MUST be no code path that returns the full key for PREVIEW.
   */
  @Test
  void preview_variant_returns_preview_key_url() {
    MediaAssetId id = seedReadyAudioAsset();

    SignedUrl signed = service.issueSignedUrl(id, DeliveryVariant.PREVIEW, Duration.ofSeconds(300));

    assertNotNull(signed.url());
    // The URL must contain the preview path segment, NOT the full HLS path
    assertEquals(
        DeliveryVariant.PREVIEW,
        signed.variant(),
        "Variant in SignedUrl must be PREVIEW");
    assertFakeUrlContainsPreviewKey(signed.url());
  }

  /**
   * INV-3: When the caller explicitly passes FULL (ownership asserted by caller) the URL must
   * target the HLS key. This is the only path that may produce the full key.
   */
  @Test
  void full_variant_with_ownership_asserted_returns_hls_key_url() {
    MediaAssetId id = seedReadyAudioAsset();

    SignedUrl signed = service.issueSignedUrl(id, DeliveryVariant.FULL, Duration.ofSeconds(300));

    assertNotNull(signed.url());
    assertEquals(
        DeliveryVariant.FULL,
        signed.variant(),
        "Variant in SignedUrl must be FULL");
    assertFakeUrlContainsHlsKey(signed.url());
  }

  /**
   * INV-3: There is NO code path that presigns hls_key without an asserted ownership decision.
   * Specifically: passing PREVIEW must NEVER produce the full HLS URL.
   * Symmetrically: passing FULL must NOT produce the preview URL.
   */
  @Test
  void preview_url_never_contains_hls_path() {
    MediaAssetId id = seedReadyAudioAsset();
    SignedUrl signed = service.issueSignedUrl(id, DeliveryVariant.PREVIEW, Duration.ofSeconds(300));
    String url = signed.url();
    if (url.contains("/hls/")) {
      throw new AssertionError(
          "INV-3 VIOLATED: PREVIEW variant produced a URL containing /hls/ (full rendition path). URL: " + url);
    }
  }

  @Test
  void full_url_never_contains_preview_path() {
    MediaAssetId id = seedReadyAudioAsset();
    SignedUrl signed = service.issueSignedUrl(id, DeliveryVariant.FULL, Duration.ofSeconds(300));
    String url = signed.url();
    if (url.contains("/preview/")) {
      throw new AssertionError(
          "Full variant should not produce a /preview/ URL. URL: " + url);
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

  private void assertFakeUrlContainsHlsKey(String url) {
    if (!url.contains("hls")) {
      throw new AssertionError(
          "Expected URL to reference HLS rendition but got: " + url);
    }
  }
}
