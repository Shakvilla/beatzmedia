package org.shakvilla.beatzmedia.podcasts.adapter.out.pricing;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.out.ModulePriceSource;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastRepository;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;

/**
 * Contributes authoritative {@code season-pass} pricing to commerce via the {@link
 * ModulePriceSource} SPI (WU-COM-4). The {@code refId} is the show's {@link PodcastId}; the price is
 * the show's {@code seasonPassPrice}. A show with no season pass configured is rejected 404 rather
 * than sold. Podcasts owns the data; commerce never reads the podcast tables.
 */
@ApplicationScoped
public class SeasonPassPriceSource implements ModulePriceSource {

  private final PodcastRepository repository;

  @Inject
  public SeasonPassPriceSource(PodcastRepository repository) {
    this.repository = repository;
  }

  @Override
  public String entityType() {
    return "season-pass";
  }

  @Override
  public PricedItem price(String refId, Map<String, Object> metadata) {
    Podcast show =
        repository
            .findShow(new PodcastId(refId))
            .orElseThrow(() -> new PriceUnavailableException("season-pass", refId));
    Money price =
        show.seasonPassPrice()
            .orElseThrow(() -> new PriceUnavailableException("season-pass", refId));
    return new PricedItem(show.title(), show.publisher(), show.image(), price);
  }
}
