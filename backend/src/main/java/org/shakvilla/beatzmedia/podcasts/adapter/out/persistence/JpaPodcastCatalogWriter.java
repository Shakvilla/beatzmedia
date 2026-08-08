package org.shakvilla.beatzmedia.podcasts.adapter.out.persistence;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.podcasts.application.port.in.PublishPodcastEpisode.PublishEpisodeCommand;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastCatalogWriter;

/** JPA adapter for the podcast-catalogue projection. */
@ApplicationScoped
public class JpaPodcastCatalogWriter implements PodcastCatalogWriter {

  private final EntityManager em;

  @Inject
  public JpaPodcastCatalogWriter(EntityManager em) {
    this.em = em;
  }

  @Override
  public void upsertShow(PublishEpisodeCommand cmd) {
    PodcastEntity show = em.find(PodcastEntity.class, cmd.showId());
    boolean isNew = show == null;
    if (isNew) {
      show = new PodcastEntity();
      show.id = cmd.showId();
      show.createdAt = Instant.now();
      show.episodeCount = 0;
      // Popularity is earned from plays, not assigned. Seeding a number here would put a fake
      // ranking signal into the browse rails on day one.
      show.popularity = 0;
      show.supportsTips = true;
    }
    show.title = cmd.showTitle();
    show.publisher = cmd.publisher();
    show.category = cmd.showCategory();
    show.image = cmd.showImage();
    show.description = cmd.showDescription();
    show.creatorAccountId = cmd.creatorAccountId();
    em.merge(show);
  }

  @Override
  public boolean upsertEpisode(PublishEpisodeCommand cmd) {
    PodcastEpisodeEntity ep = em.find(PodcastEpisodeEntity.class, cmd.episodeId());
    boolean isNew = ep == null;
    if (isNew) {
      ep = new PodcastEpisodeEntity();
      ep.id = cmd.episodeId();
      ep.createdAt = Instant.now();
    }
    ep.podcastId = cmd.showId();
    ep.title = cmd.episodeTitle();
    // podcast_episode.image is NOT NULL; per-episode art is optional in Studio, so the show cover
    // is the fallback. The service has already refused a command with no show cover.
    ep.image = cmd.episodeImage() == null || cmd.episodeImage().isBlank()
        ? cmd.showImage()
        : cmd.episodeImage();
    ep.description = cmd.episodeDescription();
    ep.durationSec = cmd.durationSec();
    ep.isPremium = cmd.premium();
    ep.priceMinor = cmd.priceMinor();
    ep.priceCurrency = cmd.priceCurrency();
    ep.isEarlyAccess = cmd.earlyAccess();
    ep.publishedAt = cmd.publishedAt();
    em.merge(ep);
    return isNew;
  }

  @Override
  public void refreshEpisodeCount(String showId) {
    // Recomputed, not incremented: an increment drifts permanently the first time a projection is
    // replayed or an episode is removed, and the count is what the browse card shows.
    em.createQuery(
            "UPDATE PodcastEntity p SET p.episodeCount = "
                + "(SELECT COUNT(e) FROM PodcastEpisodeEntity e WHERE e.podcastId = :id) "
                + "WHERE p.id = :id")
        .setParameter("id", showId)
        .executeUpdate();
  }
}
