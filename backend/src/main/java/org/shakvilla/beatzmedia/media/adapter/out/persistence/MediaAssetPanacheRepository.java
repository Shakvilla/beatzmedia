package org.shakvilla.beatzmedia.media.adapter.out.persistence;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.media.application.port.out.MediaAssetRepository;
import org.shakvilla.beatzmedia.media.domain.MediaAsset;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

/**
 * Panache JPA implementation of {@link MediaAssetRepository}. Maps domain aggregate ↔ JPA entity.
 * Owns the {@code media_asset} table — no other module reads this table. ADD §5.2 / conventions §6.
 */
@ApplicationScoped
public class MediaAssetPanacheRepository
    implements MediaAssetRepository, PanacheRepositoryBase<MediaAssetEntity, String> {

  @Override
  public MediaAsset save(MediaAsset asset) {
    MediaAssetEntity entity = MediaAssetMapper.toEntity(asset);
    getEntityManager().merge(entity);
    return asset;
  }

  @Override
  public Optional<MediaAsset> findById(MediaAssetId id) {
    return findByIdOptional(id.value()).map(MediaAssetMapper::toDomain);
  }

  @Override
  public Optional<MediaAsset> findByOwnerRefAndContentHash(OwnerRef ownerRef, String contentHash) {
    return find("ownerRef = ?1 and contentHash = ?2", ownerRef.toStorageString(), contentHash)
        .firstResultOptional()
        .map(MediaAssetMapper::toDomain);
  }
}
