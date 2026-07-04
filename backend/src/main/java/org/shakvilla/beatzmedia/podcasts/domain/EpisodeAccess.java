package org.shakvilla.beatzmedia.podcasts.domain;

/**
 * The INV-3 access decision for one episode as seen by one caller. Pure decision logic —
 * framework-free, no wall-clock (the comparison instant is resolved by the application layer via
 * the platform {@code Clock} port, never {@code Instant.now()} here). ADD §3 / §8.
 *
 * <p>Episode access state machine (ADD §8):
 *
 * <ul>
 *   <li><b>Free</b> (not premium, not early-access) → always accessible, full audio.
 *   <li><b>PremiumLocked</b> (premium, unowned) → locked; preview only, until an ownership grant
 *       moves it to <b>Owned</b>.
 *   <li><b>EarlyLocked</b> (early-access, before {@code publicAt}, unowned) → locked; preview only,
 *       until either an ownership grant (→ <b>Owned</b>) or {@code now >= publicAt} (→ <b>Free</b>).
 *   <li>An episode is accessible (full audio) as soon as it is owned, OR it is not gated at all, OR
 *       it is early-access and {@code now >= publicAt} (free to everyone from that point, even if
 *       also flagged premium — {@code publicAt} passing always frees an early-access episode).
 * </ul>
 */
public record EpisodeAccess(boolean accessible, boolean previewOnly, int previewSec) {

  /** Default preview length (seconds) absent a {@code PlatformSettings} override. */
  public static final int DEFAULT_PREVIEW_SEC = 30;

  /**
   * Compute the access decision for an episode.
   *
   * @param episode the episode (carries {@code isPremium}/{@code isEarlyAccess}/{@code publicAt})
   * @param owned whether the caller holds an active ownership grant for this episode
   * @param publicNow whether {@code now >= publicAt} (irrelevant unless {@code isEarlyAccess})
   * @param previewSec the preview clip length in seconds (from {@code PlatformSettings}, INV-3)
   */
  public static EpisodeAccess decide(
      PodcastEpisode episode, boolean owned, boolean publicNow, int previewSec) {
    boolean earlyAccessFreedNow = episode.isEarlyAccess() && publicNow;
    boolean gated = episode.isPremium() || episode.isEarlyAccess();

    boolean accessible = !gated || owned || earlyAccessFreedNow;
    boolean previewOnly = !accessible;
    return new EpisodeAccess(accessible, previewOnly, previewOnly ? previewSec : 0);
  }
}
