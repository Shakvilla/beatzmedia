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
   * Transcode the original to the FULL delivery rendition: a single AAC/M4A object at
   * {@code delivery/{id}/full.m4a}. Single-file (not HLS) so one presigned URL is fully
   * playable — an HLS playlist's segments are referenced relatively and would be unsigned
   * against the private delivery bucket. Media ADD §4.
   */
  ObjectKey transcodeFull(ObjectKey original, MediaAssetId id);

  /**
   * Transcode the first {@code previewSeconds} of the original to the PREVIEW rendition, a
   * single AAC/M4A object at {@code delivery/{id}/preview.m4a}. The clip IS the enforcement:
   * a non-owner is only ever signed this object, so INV-3 cannot be overrun client-side.
   */
  ObjectKey clipPreview(ObjectKey original, MediaAssetId id, int previewSeconds);

  /**
   * Transcode the original to the LOSSLESS delivery rendition: a single FLAC object at
   * {@code delivery/{id}/lossless.flac}. This is the download payload.
   *
   * <p>FLAC rather than the original upload: formats vary per track and sizes are unbounded, and
   * handing over the artist's master is the thing an artist disabling downloads is protecting.
   * FLAC over WAV for roughly half the size at identical fidelity.
   */
  ObjectKey transcodeLossless(ObjectKey original, MediaAssetId id);
}
