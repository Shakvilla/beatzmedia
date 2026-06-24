package org.shakvilla.beatzmedia.media.application.port.out;

import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

/**
 * Output port for audio transcoding (ffprobe / ffmpeg). The adapter invokes ffprobe/ffmpeg via
 * ProcessBuilder. ADD §4.2 / §5.2. Long-running — called from async job, never on request thread.
 */
public interface AudioTranscoderPort {

  /**
   * Probe the duration of the original file using ffprobe.
   *
   * @param original the originals-bucket key of the WAV/FLAC
   * @return duration in whole seconds
   */
  int probeDurationSec(ObjectKey original);

  /**
   * Transcode the original to a full HLS rendition and upload segments to the delivery bucket.
   *
   * @param original the originals-bucket key
   * @param id       the asset id (used to compose the delivery key prefix)
   * @return the delivery-bucket key of the HLS playlist
   */
  ObjectKey transcodeHls(ObjectKey original, MediaAssetId id);

  /**
   * Produce a physically-clipped ≤previewSeconds HLS rendition for the preview gate (INV-3).
   *
   * @param original       the originals-bucket key
   * @param id             the asset id
   * @param previewSeconds how many seconds to clip (from BEATZ_PREVIEW_SECONDS, default 30)
   * @return the delivery-bucket key of the preview playlist
   */
  ObjectKey clipPreviewHls(ObjectKey original, MediaAssetId id, int previewSeconds);
}
