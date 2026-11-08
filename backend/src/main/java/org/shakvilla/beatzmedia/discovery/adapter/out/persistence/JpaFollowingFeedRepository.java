package org.shakvilla.beatzmedia.discovery.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.discovery.application.port.out.FollowingFeedRepository;
import org.shakvilla.beatzmedia.discovery.domain.FeedKind;
import org.shakvilla.beatzmedia.discovery.domain.FollowingFeedItem;

@ApplicationScoped
public class JpaFollowingFeedRepository implements FollowingFeedRepository {

  private final EntityManager em;

  @Inject
  public JpaFollowingFeedRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void upsert(FollowingFeedItem item) {
    FollowingFeedItemEntity entity = em.find(FollowingFeedItemEntity.class, item.key());
    if (entity == null) {
      entity = new FollowingFeedItemEntity();
      entity.id = item.key();
    }
    entity.kind = item.kind();
    entity.contentId = item.contentId();
    entity.creatorKind = item.creatorKind();
    entity.creatorId = item.creatorId();
    entity.title = item.title();
    entity.subtitle = item.subtitle();
    entity.image = item.image();
    entity.route = item.route();
    entity.publishedAt = item.publishedAt();
    entity.projectedAt = item.projectedAt();
    if (!em.contains(entity)) {
      em.persist(entity);
    }
  }

  @Override
  public List<FollowingFeedItem> find(
      List<String> artistIds,
      List<String> showIds,
      Instant beforePublishedAt,
      FeedKind afterKind,
      String afterContentId,
      int limit) {
    if (artistIds.isEmpty() && showIds.isEmpty()) return List.of();

    StringBuilder jpql =
        new StringBuilder(
            "SELECT e FROM FollowingFeedItemEntity e WHERE ((e.creatorKind ="
                + " org.shakvilla.beatzmedia.discovery.domain.CreatorKind.artist"
                + " AND e.creatorId IN :artists) OR (e.creatorKind ="
                + " org.shakvilla.beatzmedia.discovery.domain.CreatorKind.show"
                + " AND e.creatorId IN :shows))");
    if (beforePublishedAt != null) {
      jpql.append(
          " AND (e.publishedAt < :publishedAt OR (e.publishedAt = :publishedAt"
              + " AND e.id > :afterId))");
    }
    jpql.append(" ORDER BY e.publishedAt DESC, e.id ASC");

    var query = em.createQuery(jpql.toString(), FollowingFeedItemEntity.class)
        .setParameter("artists", artistIds.isEmpty() ? List.of("__none__") : artistIds)
        .setParameter("shows", showIds.isEmpty() ? List.of("__none__") : showIds)
        .setMaxResults(limit);
    if (beforePublishedAt != null) {
      query.setParameter("publishedAt", beforePublishedAt)
          .setParameter("afterId", afterKind.name() + ":" + afterContentId);
    }
    return query.getResultList().stream().map(JpaFollowingFeedRepository::toDomain).toList();
  }

  private static FollowingFeedItem toDomain(FollowingFeedItemEntity entity) {
    return new FollowingFeedItem(
        entity.kind,
        entity.contentId,
        entity.creatorKind,
        entity.creatorId,
        entity.title,
        entity.subtitle,
        entity.image,
        entity.route,
        entity.publishedAt,
        entity.projectedAt);
  }
}
