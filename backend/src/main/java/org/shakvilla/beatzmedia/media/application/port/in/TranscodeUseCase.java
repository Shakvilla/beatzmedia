package org.shakvilla.beatzmedia.media.application.port.in;

import org.shakvilla.beatzmedia.media.domain.MediaAssetId;

/**
 * Input port: enqueue async transcode of an AUDIO asset to HLS + 30s preview. Idempotent per
 * assetId — re-enqueue while TRANSCODING is a no-op. LLFR-MEDIA-01.2 / ADD §4.1.
 */
public interface TranscodeUseCase {

  /**
   * Enqueue or re-enqueue a transcode job for the given asset.
   *
   * @param assetId the asset to transcode (must exist and be in UPLOADING or ERROR)
   */
  void enqueueTranscode(MediaAssetId assetId);
}
