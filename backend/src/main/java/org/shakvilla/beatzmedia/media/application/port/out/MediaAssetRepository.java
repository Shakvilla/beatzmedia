package org.shakvilla.beatzmedia.media.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.media.domain.MediaAsset;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;

/** Output port for MediaAsset persistence. ADD §4.2. */
public interface MediaAssetRepository {

  /** Persist a new or updated asset. Returns the saved aggregate. */
  MediaAsset save(MediaAsset asset);

  /** Load by primary key. */
  Optional<MediaAsset> findById(MediaAssetId id);

  /**
   * Idempotency lookup: find an existing asset for the same owner and content hash.
   * Used by uploadOriginal to return the existing handle on re-upload.
   */
  Optional<MediaAsset> findByOwnerRefAndContentHash(OwnerRef ownerRef, String contentHash);

  /**
   * The current (most recently created) asset for the given owner, if any. Used by consuming
   * modules (playback, podcasts) to resolve "the audio for this track/episode" without knowing the
   * internal {@link MediaAssetId} — a re-upload creates a new row, so "current" = latest by
   * creation time. ADD §4.2.
   */
  Optional<MediaAsset> findCurrentByOwnerRef(OwnerRef ownerRef);
}
