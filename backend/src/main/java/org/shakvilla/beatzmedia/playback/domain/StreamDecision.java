package org.shakvilla.beatzmedia.playback.domain;

/**
 * The INV-3 ownership gate, decided once per {@code GetStreamUrl} request. Pure decision logic —
 * framework-free. Playback ADD §3 / §8.
 *
 * <p>Guard: {@code ownership == FOR_SALE && !owned ⇒ PREVIEW}; otherwise {@code FULL}. Anonymous
 * callers (no account) are treated as not-owning for {@code FOR_SALE} tracks.
 */
public final class StreamDecision {

  private StreamDecision() {}

  /**
   * Resolve the {@link PlaybackMode} for a track given its commercial kind and whether the caller
   * owns it. This is the single INV-3 enforcement point in the domain — never overridable by a
   * client-supplied flag.
   */
  public static PlaybackMode decide(TrackOwnership ownership, boolean owned) {
    if (ownership == TrackOwnership.FOR_SALE && !owned) {
      return PlaybackMode.PREVIEW;
    }
    return PlaybackMode.FULL;
  }
}
