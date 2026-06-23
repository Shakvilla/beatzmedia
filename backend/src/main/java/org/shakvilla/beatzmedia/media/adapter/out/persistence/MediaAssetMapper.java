package org.shakvilla.beatzmedia.media.adapter.out.persistence;

import org.shakvilla.beatzmedia.media.domain.MediaAsset;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.MediaStatus;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;

/**
 * Bidirectional mapper between {@link MediaAsset} (domain) and {@link MediaAssetEntity} (JPA).
 * No framework annotations on the domain object. Conventions §6.
 */
class MediaAssetMapper {

  private MediaAssetMapper() {}

  static MediaAssetEntity toEntity(MediaAsset domain) {
    MediaAssetEntity e = new MediaAssetEntity();
    e.id = domain.getId().value();
    e.ownerRef = domain.getOwnerRef().toStorageString();
    e.kind = domain.getKind().name();
    e.status = domain.getStatus().name();
    e.durationSec = domain.getDurationSec();
    e.originalKey = domain.getOriginalKey().toStorageString();
    e.hlsKey = domain.getHlsKey() != null ? domain.getHlsKey().toStorageString() : null;
    e.previewKey = domain.getPreviewKey() != null ? domain.getPreviewKey().toStorageString() : null;
    e.createdAt = domain.getCreatedAt();
    e.contentHash = domain.getContentHash();
    return e;
  }

  static MediaAsset toDomain(MediaAssetEntity e) {
    return new MediaAsset(
        new MediaAssetId(e.id),
        OwnerRef.fromStorageString(e.ownerRef),
        MediaKind.valueOf(e.kind),
        MediaStatus.valueOf(e.status),
        e.durationSec != null ? e.durationSec : 0,
        ObjectKey.fromStorageString(e.originalKey),
        e.hlsKey != null ? ObjectKey.fromStorageString(e.hlsKey) : null,
        e.previewKey != null ? ObjectKey.fromStorageString(e.previewKey) : null,
        e.createdAt,
        e.contentHash);
  }
}
