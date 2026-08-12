package org.shakvilla.beatzmedia.podcasts.application.port.in;

/**
 * Projects a published Studio episode into the fan-facing podcast catalogue.
 *
 * <p><strong>Why a port and not a shared table.</strong> Studio owns {@code studio_podcast_show} /
 * {@code studio_episode}; podcasts owns {@code podcast} / {@code podcast_episode}. Neither module
 * may read the other's tables, so Studio pushes a complete command through this port instead. That
 * keeps the write inside the module that owns the rows and their invariants (episode_count,
 * popularity, the image NOT NULL contract).
 *
 * <p>The show is upserted alongside the episode: a show becomes fan-visible on its FIRST published
 * episode, never before. A podcast with no episodes is an empty shell, and creating one at
 * show-creation time would put silent placeholders in the browse list.
 */
public interface PublishPodcastEpisode {

  /**
   * Upserts the show and the episode. Idempotent on {@code episodeId} — the same episode
   * republished (a re-fired event, a scheduler retry) must not duplicate a row or double-count
   * {@code episode_count}.
   *
   * @throws org.shakvilla.beatzmedia.platform.domain.ValidationException if the show has no cover
   *     image; {@code podcast.image} is NOT NULL and inventing one is not an option.
   */
  void publish(PublishEpisodeCommand command);

  /**
   * Everything podcasts needs to build both rows, supplied by the caller because podcasts cannot
   * read studio's tables.
   *
   * @param showImage the show's cover; null is rejected rather than defaulted
   * @param episodeImage per-episode art, falling back to the show cover when absent
   */
  record PublishEpisodeCommand(
      String showId,
      String showTitle,
      String showCategory,
      String showImage,
      String showDescription,
      String publisher,
      String creatorAccountId,
      String episodeId,
      String episodeTitle,
      String episodeDescription,
      String episodeImage,
      int durationSec,
      boolean premium,
      Long priceMinor,
      String priceCurrency,
      boolean earlyAccess,
      java.time.Instant publishedAt) {}
}
