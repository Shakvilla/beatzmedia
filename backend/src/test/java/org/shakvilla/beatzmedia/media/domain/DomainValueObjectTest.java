package org.shakvilla.beatzmedia.media.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for domain value objects: OwnerRef, ObjectKey, MediaAssetId, SignedUrl, MediaHandle.
 * Ensures serialization/parsing round-trips and validation guards hold. ADD §3.
 */
class DomainValueObjectTest {

  // ---- OwnerRef ----

  @Test
  void owner_ref_storage_round_trip() {
    OwnerRef ref = new OwnerRef("catalog", "track-abc123");
    String stored = ref.toStorageString();
    OwnerRef parsed = OwnerRef.fromStorageString(stored);
    assertEquals(ref.module(), parsed.module());
    assertEquals(ref.entityId(), parsed.entityId());
    assertEquals("catalog:track-abc123", stored);
  }

  @Test
  void owner_ref_parse_invalid_throws() {
    assertThrows(IllegalArgumentException.class, () -> OwnerRef.fromStorageString("nocolon"));
    assertThrows(IllegalArgumentException.class, () -> OwnerRef.fromStorageString(":empty-module"));
  }

  // ---- ObjectKey ----

  @Test
  void object_key_storage_round_trip() {
    ObjectKey key = new ObjectKey("beatz-media-delivery", "delivery/id123/hls/playlist.m3u8");
    String stored = key.toStorageString();
    ObjectKey parsed = ObjectKey.fromStorageString(stored);
    assertEquals(key.bucket(), parsed.bucket());
    assertEquals(key.key(), parsed.key());
  }

  @Test
  void object_key_parse_invalid_throws() {
    assertThrows(IllegalArgumentException.class, () -> ObjectKey.fromStorageString("nopipe"));
    assertThrows(IllegalArgumentException.class, () -> ObjectKey.fromStorageString("|no-bucket"));
  }

  // ---- MediaAssetId ----

  @Test
  void media_asset_id_blank_throws() {
    assertThrows(IllegalArgumentException.class, () -> new MediaAssetId(""));
    assertThrows(IllegalArgumentException.class, () -> new MediaAssetId("   "));
    assertThrows(IllegalArgumentException.class, () -> new MediaAssetId(null));
  }

  @Test
  void media_asset_id_to_string() {
    assertEquals("abc-123", new MediaAssetId("abc-123").toString());
  }

  // ---- SignedUrl ----

  @Test
  void signed_url_carries_all_fields() {
    Instant expiry = Instant.parse("2026-12-31T23:59:59Z");
    SignedUrl url = new SignedUrl("https://example.com/presigned", DeliveryVariant.PREVIEW, expiry);
    assertEquals("https://example.com/presigned", url.url());
    assertEquals(DeliveryVariant.PREVIEW, url.variant());
    assertEquals(expiry, url.expiresAt());
  }

  // ---- MediaHandle ----

  @Test
  void media_handle_fields() {
    MediaHandle handle = new MediaHandle(
        new MediaAssetId("h001"), MediaKind.AUDIO, 120, MediaStatus.UPLOADING);
    assertEquals("h001", handle.assetId().value());
    assertEquals(MediaKind.AUDIO, handle.kind());
    assertEquals(120, handle.durationSec());
    assertEquals(MediaStatus.UPLOADING, handle.status());
  }

  // ---- MediaAsset.toHandle ----

  @Test
  void media_asset_to_handle_projection() {
    MediaAsset asset = MediaAsset.createUploading(
        new MediaAssetId("h002"),
        new OwnerRef("catalog", "t001"),
        MediaKind.AUDIO,
        new ObjectKey("orig", "key"),
        42,
        Instant.parse("2026-01-01T00:00:00Z"),
        "hash-xyz");
    MediaHandle handle = asset.toHandle();
    assertEquals("h002", handle.assetId().value());
    assertEquals(MediaKind.AUDIO, handle.kind());
    assertEquals(42, handle.durationSec());
    assertEquals(MediaStatus.UPLOADING, handle.status());
  }

  // ---- MediaReady ----

  @Test
  void media_ready_event_fields() {
    MediaAssetId id = new MediaAssetId("mr001");
    OwnerRef ref = new OwnerRef("catalog", "t001");
    MediaReady event = new MediaReady(id, ref, MediaKind.AUDIO);
    assertEquals(id, event.assetId());
    assertEquals(ref, event.ownerRef());
    assertEquals(MediaKind.AUDIO, event.kind());
  }
}
