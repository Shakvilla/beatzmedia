package org.shakvilla.beatzmedia.media.application.port.out;

import java.time.Duration;

import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.SignedUrl;

/**
 * The primary output port consumed by catalog, podcasts, studio, and playback modules.
 * Modules must depend only on this interface — never on internal media classes. ADD §4.2.
 */
public interface MediaService {

  /** Stream a multipart part to object storage; validate; persist UPLOADING asset. */
  MediaHandle uploadOriginal(UploadCommand command);

  /** Probe duration (whole seconds) via ffprobe. */
  int probeDuration(MediaAssetId assetId);

  /** Enqueue async HLS transcode for the given asset. */
  void transcodeToHls(MediaAssetId assetId);

  /** Enqueue async 30s preview clip generation (INV-3). */
  void generatePreviewClip(MediaAssetId assetId);

  /** Validate and emit processed delivery variants for artwork. */
  MediaHandle processArtwork(MediaAssetId assetId);

  /**
   * INV-3 enforcement point: presign delivery key. FULL only when ownership asserted by caller.
   *
   * @param assetId  the asset
   * @param variant  FULL or PREVIEW
   * @param ttl      signed URL time-to-live
   */
  SignedUrl issueSignedUrl(MediaAssetId assetId, DeliveryVariant variant, Duration ttl);
}
