package org.shakvilla.beatzmedia.media.fakes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.media.application.port.out.MediaAssetRepository;
import org.shakvilla.beatzmedia.media.domain.MediaAsset;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;

/** In-memory fake for {@link MediaAssetRepository} used in unit tests. */
public class FakeMediaAssetRepository implements MediaAssetRepository {

  private final Map<String, MediaAsset> store = new HashMap<>();

  @Override
  public MediaAsset save(MediaAsset asset) {
    store.put(asset.getId().value(), asset);
    return asset;
  }

  @Override
  public Optional<MediaAsset> findById(MediaAssetId id) {
    return Optional.ofNullable(store.get(id.value()));
  }

  @Override
  public Optional<MediaAsset> findByOwnerRefAndContentHash(OwnerRef ownerRef, String contentHash) {
    return store.values().stream()
        .filter(
            a ->
                a.getOwnerRef().toStorageString().equals(ownerRef.toStorageString())
                    && contentHash.equals(a.getContentHash()))
        .findFirst();
  }

  @Override
  public Optional<MediaAsset> findCurrentByOwnerRef(OwnerRef ownerRef) {
    return store.values().stream()
        .filter(a -> a.getOwnerRef().toStorageString().equals(ownerRef.toStorageString()))
        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
        .findFirst();
  }

  public int size() {
    return store.size();
  }
}
