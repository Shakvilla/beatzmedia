package org.shakvilla.beatzmedia.podcasts.adapter.out.pricing;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.out.ModulePriceSource;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastRepository;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastEpisode;

/**
 * Contributes authoritative {@code episode} pricing to commerce via the {@link ModulePriceSource}
 * SPI (WU-COM-4). Podcasts owns the data and mapping; commerce never reads the podcast tables —
 * the {@code podcasts -> commerce} edge here matches the existing ownership-read edge. Mirrors the
 * WU-SRCH-2 {@code catalog.adapter.out.search.*IndexSource} pattern (inject the module's own
 * repository, map to the SPI type).
 *
 * <p>The {@code refId} is the {@link EpisodeId}. Only a priced (premium/early-access) episode is
 * purchasable — a free episode carries no price and is rejected 404 rather than sold for ₵0.
 */
@ApplicationScoped
public class EpisodePriceSource implements ModulePriceSource {

  private final PodcastRepository repository;

  @Inject
  public EpisodePriceSource(PodcastRepository repository) {
    this.repository = repository;
  }

  @Override
  public String entityType() {
    return "episode";
  }

  @Override
  public PricedItem price(String refId, Map<String, Object> metadata) {
    PodcastEpisode episode =
        repository
            .findEpisode(new EpisodeId(refId))
            .orElseThrow(() -> new PriceUnavailableException("episode", refId));
    Money price =
        episode.price().orElseThrow(() -> new PriceUnavailableException("episode", refId));
    // Show title as the display subtitle (fidelity, WU-COM-3); never affects the authoritative price.
    String subtitle =
        repository.findShow(episode.podcastId()).map(show -> show.title()).orElse(null);
    return new PricedItem(episode.title(), subtitle, episode.image(), price);
  }
}
