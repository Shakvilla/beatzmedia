package org.shakvilla.beatzmedia.media.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;

/**
 * Input port: resolve the current {@link MediaAssetId} for a consuming module's entity (e.g. a
 * catalog track), so the caller can then call {@link IssueDeliveryUrlUseCase} without needing to
 * know or persist the internal asset id itself. Added for WU-PLY-1 (playback resolves a track's
 * audio asset through this port rather than reading {@code media_asset} directly). ADD §4.1.
 */
public interface FindAssetForOwnerUseCase {

  /**
   * @param ownerRef the consuming module's owner reference (e.g. {@code ("catalog", trackId)})
   * @return the current asset id, if the owner has ever uploaded media
   */
  Optional<MediaAssetId> findAssetIdForOwner(OwnerRef ownerRef);
}
