package org.shakvilla.beatzmedia.podcasts.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastView;
import org.shakvilla.beatzmedia.podcasts.application.service.ListPodcastsService;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.podcasts.fakes.FakePodcastRepository;

/** Unit tests for {@link ListPodcastsService} — browse shows (LLFR-PODCAST-01.1). */
@Tag("unit")
class ListPodcastsServiceTest {

  private static final Instant CREATED = Instant.parse("2026-06-01T00:00:00Z");

  FakePodcastRepository repository;
  ListPodcastsService service;

  @BeforeEach
  void setUp() {
    repository =
        new FakePodcastRepository()
            .withShow(
                new Podcast(
                    new PodcastId("show-culture"), "Culture Show", "Pub", "img.png",
                    PodcastCategory.CULTURE, "desc", 10, 90, Money.ofMinor(1200, Currency.GHS),
                    true, CREATED))
            .withShow(
                new Podcast(
                    new PodcastId("show-tech"), "Tech Show", "Pub", "img.png",
                    PodcastCategory.TECH, "desc", 5, 50, null, false, CREATED));
    service = new ListPodcastsService(repository);
  }

  @Test
  void noFilter_returnsAllShows() {
    Page<PodcastView> page = service.list(Optional.empty(), PageRequest.defaults());
    assertEquals(2, page.total());
    assertEquals(2, page.items().size());
  }

  @Test
  void categoryFilter_returnsOnlyMatchingShows() {
    Page<PodcastView> page =
        service.list(Optional.of(PodcastCategory.CULTURE), PageRequest.defaults());
    assertEquals(1, page.total());
    assertEquals("show-culture", page.items().get(0).id());
    assertEquals("Culture", page.items().get(0).category());
  }

  @Test
  void seasonPassPrice_isMappedToMoneyView() {
    Page<PodcastView> page =
        service.list(Optional.of(PodcastCategory.CULTURE), PageRequest.defaults());
    PodcastView view = page.items().get(0);
    assertTrue(view.seasonPassPrice() != null);
    assertEquals("GHS", view.seasonPassPrice().currency());
    assertEquals(0, view.seasonPassPrice().amount().compareTo(new java.math.BigDecimal("12.00")));
  }
}
