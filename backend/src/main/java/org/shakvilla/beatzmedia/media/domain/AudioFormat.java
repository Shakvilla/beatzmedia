package org.shakvilla.beatzmedia.media.domain;

/**
 * Accepted audio upload formats. ADD §3 / §9.
 *
 * <p>WAV and FLAC are lossless masters. MP3 is lossy (ADR-35). M4A may carry AAC or lossless
 * ALAC; without inspecting the codec, the Studio wizard conservatively shows a quality advisory.
 */
public enum AudioFormat {
  WAV(false),
  FLAC(false),
  MP3(true),
  M4A(true);

  private final boolean lossy;

  AudioFormat(boolean lossy) {
    this.lossy = lossy;
  }

  /** True when the source is lossy or may be lossy (M4A can contain AAC or ALAC). */
  public boolean lossy() {
    return lossy;
  }
}
