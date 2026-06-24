package org.shakvilla.beatzmedia.media.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MediaAsset} state-machine transitions. ADD §8 / §3.
 */
class MediaAssetStateMachineTest {

  private MediaAsset newUploadingAudioAsset() {
    return MediaAsset.createUploading(
        new MediaAssetId("test-asset-001"),
        new OwnerRef("catalog", "track-001"),
        MediaKind.AUDIO,
        new ObjectKey("originals", "audio/test-asset-001"),
        0,
        Instant.parse("2026-01-01T10:00:00Z"),
        "hash123");
  }

  // ---- UPLOADING → TRANSCODING ----

  @Test
  void uploading_to_transcoding_is_valid() {
    MediaAsset asset = newUploadingAudioAsset();
    assertEquals(MediaStatus.UPLOADING, asset.getStatus());
    asset.startTranscoding();
    assertEquals(MediaStatus.TRANSCODING, asset.getStatus());
  }

  @Test
  void start_transcoding_is_idempotent_when_already_transcoding() {
    MediaAsset asset = newUploadingAudioAsset();
    asset.startTranscoding();
    asset.startTranscoding(); // should not throw
    assertEquals(MediaStatus.TRANSCODING, asset.getStatus());
  }

  @Test
  void cannot_retranscode_ready_asset() {
    MediaAsset asset = newUploadingAudioAsset();
    asset.startTranscoding();
    ObjectKey hls = new ObjectKey("delivery", "delivery/id/hls/playlist.m3u8");
    ObjectKey prev = new ObjectKey("delivery", "delivery/id/preview/preview.m3u8");
    asset.markReady(hls, prev, 180);
    assertThrows(IllegalStateException.class, asset::startTranscoding);
  }

  // ---- TRANSCODING → READY ----

  @Test
  void transcoding_to_ready_sets_keys_and_duration() {
    MediaAsset asset = newUploadingAudioAsset();
    asset.startTranscoding();
    ObjectKey hls = new ObjectKey("delivery", "delivery/001/hls/playlist.m3u8");
    ObjectKey prev = new ObjectKey("delivery", "delivery/001/preview/preview.m3u8");
    asset.markReady(hls, prev, 213);
    assertEquals(MediaStatus.READY, asset.getStatus());
    assertEquals(hls, asset.getHlsKey());
    assertEquals(prev, asset.getPreviewKey());
    assertEquals(213, asset.getDurationSec());
  }

  @Test
  void audio_ready_requires_both_hls_and_preview_keys() {
    MediaAsset asset = newUploadingAudioAsset();
    asset.startTranscoding();
    ObjectKey hls = new ObjectKey("delivery", "key");
    assertThrows(IllegalArgumentException.class, () -> asset.markReady(hls, null, 10));
    assertThrows(IllegalArgumentException.class, () -> asset.markReady(null, hls, 10));
  }

  // ---- → ERROR ----

  @Test
  void transcoding_to_error() {
    MediaAsset asset = newUploadingAudioAsset();
    asset.startTranscoding();
    asset.markError();
    assertEquals(MediaStatus.ERROR, asset.getStatus());
  }

  @Test
  void error_can_retry_transcoding() {
    MediaAsset asset = newUploadingAudioAsset();
    asset.startTranscoding();
    asset.markError();
    asset.startTranscoding(); // retry — should be valid (ERROR → TRANSCODING)
    assertEquals(MediaStatus.TRANSCODING, asset.getStatus());
  }

  // ---- resolveDeliveryKey / INV-3 ----

  @Test
  void resolve_delivery_key_not_ready_throws() {
    MediaAsset asset = newUploadingAudioAsset();
    assertThrows(
        IllegalStateException.class,
        () -> asset.resolveDeliveryKey(DeliveryVariant.FULL));
  }
}
