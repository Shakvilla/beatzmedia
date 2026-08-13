package org.shakvilla.beatzmedia.media.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * LOSSLESS is a third rendition beside FULL and PREVIEW. It must never fall back to FULL: a
 * download that silently hands over 128k AAC on a platform selling lossless masters is exactly the
 * "claims what it did not do" failure this feature exists to avoid.
 */
@Tag("unit")
class MediaAssetLosslessTest {

  private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

  private static MediaAsset readyAsset() {
    MediaAsset a = MediaAsset.createUploading(
        new MediaAssetId("m1"), new OwnerRef("track", "t1"), MediaKind.AUDIO,
        new ObjectKey("originals", "o/m1.wav"), 0, NOW, "hash");
    a.markReady(new ObjectKey("delivery", "d/m1/full.m4a"),
        new ObjectKey("delivery", "d/m1/preview.m4a"), 180);
    return a;
  }

  @Test
  void losslessKeyIsResolvedOnceSet() {
    MediaAsset a = readyAsset();
    a.markLosslessReady(new ObjectKey("delivery", "d/m1/lossless.flac"));

    assertEquals("d/m1/lossless.flac",
        a.resolveDeliveryKey(DeliveryVariant.LOSSLESS).key());
  }

  @Test
  void anAssetWithNoLosslessRenditionThrowsRatherThanFallingBackToFull() {
    MediaAsset a = readyAsset();

    assertThrows(IllegalStateException.class,
        () -> a.resolveDeliveryKey(DeliveryVariant.LOSSLESS));
  }

  @Test
  void addingLosslessDoesNotDisturbFullOrPreview() {
    MediaAsset a = readyAsset();
    a.markLosslessReady(new ObjectKey("delivery", "d/m1/lossless.flac"));

    assertEquals("d/m1/full.m4a", a.resolveDeliveryKey(DeliveryVariant.FULL).key());
    assertEquals("d/m1/preview.m4a", a.resolveDeliveryKey(DeliveryVariant.PREVIEW).key());
  }
}
