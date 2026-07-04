package org.shakvilla.beatzmedia.podcasts.adapter.out.persistence;

import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastEpisode;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;

/** Maps {@code podcast}/{@code podcast_episode} JPA entities to/from domain objects. ADD §5.2/§7. */
final class PodcastEntityMapper {

  private PodcastEntityMapper() {}

  static Podcast toDomain(PodcastEntity e) {
    Money seasonPassPrice =
        e.seasonPassPriceMinor != null
            ? Money.ofMinor(e.seasonPassPriceMinor, Currency.valueOf(e.seasonPassCurrency))
            : null;
    return new Podcast(
        new PodcastId(e.id),
        e.title,
        e.publisher,
        e.image,
        PodcastCategory.fromWireValue(e.category),
        e.description,
        e.episodeCount,
        e.popularity,
        seasonPassPrice,
        e.supportsTips,
        e.createdAt);
  }

  static PodcastEpisode toDomain(PodcastEpisodeEntity e) {
    Money price =
        e.priceMinor != null ? Money.ofMinor(e.priceMinor, Currency.valueOf(e.priceCurrency)) : null;
    return new PodcastEpisode(
        new EpisodeId(e.id),
        new PodcastId(e.podcastId),
        e.title,
        e.image,
        e.description,
        e.durationSec,
        e.episodeNumber,
        e.isPremium,
        price,
        e.isEarlyAccess,
        e.publicAt,
        e.mediaAssetId,
        e.publishedAt,
        e.createdAt);
  }
}
