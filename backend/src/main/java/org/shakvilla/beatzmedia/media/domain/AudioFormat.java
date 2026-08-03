package org.shakvilla.beatzmedia.media.domain;

/**
 * Accepted audio upload formats. ADD §3 / §9.
 *
 * <p>WAV and FLAC are lossless masters. MP3 is admitted too (ADR-35): requiring a lossless master
 * turned away artists who genuinely only have an MP3, which is a real supply barrier in this
 * market. It is {@link #lossy()}, and the Studio wizard warns before upload, because transcoding
 * an MP3 to the AAC delivery rendition is a second lossy generation that the buyer pays to own.
 */
public enum AudioFormat {
  WAV(false),
  FLAC(false),
  MP3(true);

  private final boolean lossy;

  AudioFormat(boolean lossy) {
    this.lossy = lossy;
  }

  /** True when the source is already lossy, so the delivery rendition is a second generation. */
  public boolean lossy() {
    return lossy;
  }
}
