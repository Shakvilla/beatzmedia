package org.shakvilla.beatzmedia.podcasts.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastRepository;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastEpisode;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;

/**
 * JPA implementation of {@link PodcastRepository}. Reads/writes only {@code podcast} /
 * {@code podcast_episode}; no cross-module joins. Transaction boundary = the application service.
 * ADD §5.2.
 */
@ApplicationScoped
public class PodcastJpaRepository implements PodcastRepository {

  private final EntityManager em;

  @Inject
  public PodcastJpaRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Page<Podcast> findShows(Optional<PodcastCategory> category, PageRequest page) {
    String whereJpql =
        category.isPresent() ? "FROM PodcastEntity p WHERE p.category = :category" : "FROM PodcastEntity p";

    TypedQuery<Long> countQuery = em.createQuery("SELECT COUNT(p) " + whereJpql, Long.class);
    TypedQuery<PodcastEntity> listQuery =
        em.createQuery(
            "SELECT p " + whereJpql + " ORDER BY p.popularity DESC, p.id", PodcastEntity.class);

    category.ifPresent(
        c -> {
          countQuery.setParameter("category", c.wireValue());
          listQuery.setParameter("category", c.wireValue());
        });

    long total = countQuery.getSingleResult();
    List<Podcast> items =
        listQuery
            .setFirstResult(page.offset())
            .setMaxResults(page.size())
            .getResultList()
            .stream()
            .map(PodcastEntityMapper::toDomain)
            .toList();

    return Page.of(items, page.page(), page.size(), total);
  }

  @Override
  public Optional<Podcast> findShow(PodcastId id) {
    PodcastEntity e = em.find(PodcastEntity.class, id.value());
    return e == null ? Optional.empty() : Optional.of(PodcastEntityMapper.toDomain(e));
  }

  @Override
  public List<PodcastEpisode> findEpisodes(PodcastId id) {
    return em.createQuery(
            "SELECT e FROM PodcastEpisodeEntity e WHERE e.podcastId = :pid ORDER BY e.publishedAt DESC",
            PodcastEpisodeEntity.class)
        .setParameter("pid", id.value())
        .getResultList()
        .stream()
        .map(PodcastEntityMapper::toDomain)
        .toList();
  }

  @Override
  public Optional<PodcastEpisode> findEpisode(EpisodeId id) {
    PodcastEpisodeEntity e = em.find(PodcastEpisodeEntity.class, id.value());
    return e == null ? Optional.empty() : Optional.of(PodcastEntityMapper.toDomain(e));
  }
}
