package org.shakvilla.beatzmedia.media.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.domain.MediaAsset;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.MediaStatus;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;

/**
 * Unit tests for {@link MediaAssetMapper} — domain ↔ JPA entity round-trip.
 * Conventions §6 / ADD §5.2.
 */
class MediaAssetMapperTest {

  private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

  @Test
  void uploading_audio_asset_round_trips() {
    MediaAsset domain = MediaAsset.createUploading(
        new MediaAssetId("map-001"),
        new OwnerRef("catalog", "track-001"),
        MediaKind.AUDIO,
        new ObjectKey("beatz-media-originals", "originals/audio/map-001"),
        0,
        NOW,
        "sha256-hash-abc");

    MediaAssetEntity entity = MediaAssetMapper.toEntity(domain);
    assertEquals("map-001", entity.id);
    assertEquals("catalog:track-001", entity.ownerRef);
    assertEquals("AUDIO", entity.kind);
    assertEquals("UPLOADING", entity.status);
    assertEquals(0, entity.durationSec);
    assertEquals(
        "beatz-media-originals|originals/audio/map-001", entity.originalKey);
    assertNull(entity.hlsKey);
    assertNull(entity.previewKey);
    assertEquals("sha256-hash-abc", entity.contentHash);
    assertEquals(NOW, entity.createdAt);

    MediaAsset restored = MediaAssetMapper.toDomain(entity);
    assertEquals(domain.getId(), restored.getId());
    assertEquals(domain.getOwnerRef().toStorageString(), restored.getOwnerRef().toStorageString());
    assertEquals(domain.getKind(), restored.getKind());
    assertEquals(domain.getStatus(), restored.getStatus());
    assertEquals(domain.getDurationSec(), restored.getDurationSec());
    assertNull(restored.getHlsKey());
    assertNull(restored.getPreviewKey());
    assertEquals("sha256-hash-abc", restored.getContentHash());
  }

  @Test
  void ready_audio_asset_with_hls_and_preview_round_trips() {
    MediaAsset domain = new MediaAsset(
        new MediaAssetId("map-002"),
        new OwnerRef("catalog", "track-002"),
        MediaKind.AUDIO,
        MediaStatus.READY,
        185,
        new ObjectKey("beatz-media-originals", "originals/audio/map-002"),
        new ObjectKey("beatz-media-delivery", "delivery/map-002/hls/playlist.m3u8"),
        new ObjectKey("beatz-media-delivery", "delivery/map-002/preview/preview.m3u8"),
        NOW,
        "hash-ready");

    MediaAssetEntity entity = MediaAssetMapper.toEntity(domain);
    assertEquals("READY", entity.status);
    assertEquals(185, entity.durationSec);
    assertEquals(
        "beatz-media-delivery|delivery/map-002/hls/playlist.m3u8", entity.hlsKey);
    assertEquals(
        "beatz-media-delivery|delivery/map-002/preview/preview.m3u8", entity.previewKey);

    MediaAsset restored = MediaAssetMapper.toDomain(entity);
    assertEquals(MediaStatus.READY, restored.getStatus());
    assertEquals(185, restored.getDurationSec());
    assertEquals("delivery/map-002/hls/playlist.m3u8", restored.getHlsKey().key());
    assertEquals("delivery/map-002/preview/preview.m3u8", restored.getPreviewKey().key());
  }

  @Test
  void null_hls_and_preview_survive_round_trip() {
    MediaAssetEntity entity = new MediaAssetEntity();
    entity.id = "map-003";
    entity.ownerRef = "catalog:track-003";
    entity.kind = "ARTWORK";
    entity.status = "TRANSCODING";
    entity.durationSec = null;
    entity.originalKey = "beatz-media-originals|originals/artwork/map-003";
    entity.hlsKey = null;
    entity.previewKey = null;
    entity.createdAt = NOW;
    entity.contentHash = "hash-null";

    MediaAsset domain = MediaAssetMapper.toDomain(entity);
    assertEquals(MediaKind.ARTWORK, domain.getKind());
    assertEquals(MediaStatus.TRANSCODING, domain.getStatus());
    assertEquals(0, domain.getDurationSec()); // null → 0
    assertNull(domain.getHlsKey());
    assertNull(domain.getPreviewKey());
  }
}
