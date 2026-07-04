package org.shakvilla.beatzmedia.podcasts.application.service;

import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.podcasts.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastEpisodeView;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastView;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeAccess;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastEpisode;

/** Maps podcasts domain aggregates to their wire read-models. ADD §6. */
final class PodcastMapper {

  private PodcastMapper() {}

  static PodcastView toView(Podcast podcast) {
    return new PodcastView(
        podcast.id().value(),
        podcast.title(),
        podcast.publisher(),
        podcast.image(),
        podcast.category().wireValue(),
        podcast.description().orElse(null),
        podcast.episodeCount(),
        podcast.popularity(),
        podcast.seasonPassPrice().map(PodcastMapper::toMoneyView).orElse(null),
        podcast.supportsTips());
  }

  /**
   * Maps an episode to its wire read-model, decorated with the caller's {@code isOwned} state and
   * the pre-computed {@link EpisodeAccess} (used by the resource/tests; the DTO itself carries no
   * preview/audio URL — that is fetched per-episode via {@code GetEpisodeStreamUrl}, ADD §8).
   */
  static PodcastEpisodeView toView(
      PodcastEpisode episode, String showTitle, boolean owned) {
    return new PodcastEpisodeView(
        episode.id().value(),
        episode.podcastId().value(),
        episode.title(),
        showTitle,
        episode.image(),
        episode.durationSec(),
        episode.publishedAt().toString(),
        episode.description().orElse(null),
        episode.episodeNumber().orElse(null),
        episode.isPremium() ? Boolean.TRUE : null,
        episode.price().map(PodcastMapper::toMoneyView).orElse(null),
        episode.isGated() ? Boolean.valueOf(owned) : null,
        episode.isEarlyAccess() ? Boolean.TRUE : null,
        episode.publicAt().map(Object::toString).orElse(null));
  }

  private static MoneyView toMoneyView(Money money) {
    return MoneyView.ofMinor(money.minor(), money.currency().name());
  }
}
