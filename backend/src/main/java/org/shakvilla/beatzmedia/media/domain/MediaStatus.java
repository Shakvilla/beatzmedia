package org.shakvilla.beatzmedia.media.domain;

/**
 * Lifecycle status of a {@link MediaAsset}. State machine: UPLOADING → TRANSCODING → READY / ERROR.
 * ADD §3 / §8.
 */
public enum MediaStatus {
  UPLOADING,
  TRANSCODING,
  READY,
  ERROR
}
