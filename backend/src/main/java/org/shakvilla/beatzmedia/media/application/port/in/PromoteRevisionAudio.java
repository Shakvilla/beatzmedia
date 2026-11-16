package org.shakvilla.beatzmedia.media.application.port.in;

import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;

/** Promotes a fully processed revision upload to the canonical owner after release approval. */
public interface PromoteRevisionAudio {
  boolean isReady(MediaAssetId assetId);

  MediaHandle promote(MediaAssetId assetId, OwnerRef canonicalOwner);
}
