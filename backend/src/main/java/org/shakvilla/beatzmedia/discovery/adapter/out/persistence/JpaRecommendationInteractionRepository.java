package org.shakvilla.beatzmedia.discovery.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository;

@ApplicationScoped
public class JpaRecommendationInteractionRepository implements RecommendationInteractionRepository {

  private final EntityManager em;

  @Inject
  public JpaRecommendationInteractionRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void insertAttributions(List<Attribution> attributions) {
    for (Attribution item : attributions) {
      em.createNativeQuery("""
          insert into recommendation_attribution (
            id, account_id, content_kind, content_id, source_type, source_id,
            surface, reason_code, ranking_version, position, created_at, expires_at
          ) values (
            :id, :accountId, :contentKind, :contentId, :sourceType, :sourceId,
            :surface, :reasonCode, :rankingVersion, :position, :createdAt, :expiresAt
          )
          """)
          .setParameter("id", item.id())
          .setParameter("accountId", item.accountId())
          .setParameter("contentKind", item.contentKind())
          .setParameter("contentId", item.contentId())
          .setParameter("sourceType", item.sourceType())
          .setParameter("sourceId", item.sourceId())
          .setParameter("surface", item.surface())
          .setParameter("reasonCode", item.reasonCode())
          .setParameter("rankingVersion", item.rankingVersion())
          .setParameter("position", item.position())
          .setParameter("createdAt", item.createdAt())
          .setParameter("expiresAt", item.expiresAt())
          .executeUpdate();
    }
  }

  @Override
  public Set<String> suppressedKeys(String accountId, List<String> targetKeys) {
    if (targetKeys == null || targetKeys.isEmpty()) return Set.of();
    @SuppressWarnings("unchecked")
    List<String> rows = em.createNativeQuery("""
        select target_key
        from recommendation_suppression
        where account_id = :accountId and target_key in (:targetKeys)
        """, String.class)
        .setParameter("accountId", accountId)
        .setParameter("targetKeys", targetKeys)
        .getResultList();
    return Set.copyOf(new HashSet<>(rows));
  }

  @Override
  public Optional<Attribution> attribution(
      String recommendationId, String accountId, Instant now) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows = em.createNativeQuery("""
        select id, account_id, content_kind, content_id, source_type, source_id,
               surface, reason_code, ranking_version, position, created_at, expires_at
        from recommendation_attribution
        where id = :id and account_id = :accountId and expires_at > :now
        """)
        .setParameter("id", recommendationId)
        .setParameter("accountId", accountId)
        .setParameter("now", now)
        .getResultList();
    return rows.stream().findFirst().map(JpaRecommendationInteractionRepository::mapAttribution);
  }

  @Override
  public boolean insertEvent(
      String eventId, String recommendationId, String eventType, Instant receivedAt) {
    return em.createNativeQuery("""
        insert into recommendation_event (event_id, recommendation_id, event_type, received_at)
        values (:eventId, :recommendationId, :eventType, :receivedAt)
        on conflict (event_id) do nothing
        """)
        .setParameter("eventId", eventId)
        .setParameter("recommendationId", recommendationId)
        .setParameter("eventType", eventType)
        .setParameter("receivedAt", receivedAt)
        .executeUpdate() == 1;
  }

  @Override
  public String upsertSuppression(Suppression suppression) {
    return (String) em.createNativeQuery("""
        insert into recommendation_suppression (
          id, account_id, target_key, target_type, target_kind, target_id, created_at
        ) values (
          :id, :accountId, :targetKey, :targetType, :targetKind, :targetId, :createdAt
        )
        on conflict (account_id, target_key) do update
        set target_key = excluded.target_key
        returning id
        """, String.class)
        .setParameter("id", suppression.id())
        .setParameter("accountId", suppression.accountId())
        .setParameter("targetKey", suppression.targetKey())
        .setParameter("targetType", suppression.targetType())
        .setParameter("targetKind", suppression.targetKind())
        .setParameter("targetId", suppression.targetId())
        .setParameter("createdAt", suppression.createdAt())
        .getSingleResult();
  }

  @Override
  public boolean deleteSuppression(String suppressionId, String accountId) {
    return em.createNativeQuery("""
        delete from recommendation_suppression where id = :id and account_id = :accountId
        """)
        .setParameter("id", suppressionId)
        .setParameter("accountId", accountId)
        .executeUpdate() == 1;
  }

  @Override
  public int deleteExpired(Instant now, int limit) {
    return em.createNativeQuery("""
        with expired as (
          select id from recommendation_attribution
          where expires_at <= :now
          order by expires_at, id
          limit :limit
        )
        delete from recommendation_attribution
        where id in (select id from expired)
        """)
        .setParameter("now", now)
        .setParameter("limit", limit)
        .executeUpdate();
  }

  private static Attribution mapAttribution(Object[] row) {
    return new Attribution(
        (String) row[0],
        (String) row[1],
        (String) row[2],
        (String) row[3],
        (String) row[4],
        (String) row[5],
        (String) row[6],
        (String) row[7],
        (String) row[8],
        ((Number) row[9]).intValue(),
        instant(row[10]),
        instant(row[11]));
  }

  private static Instant instant(Object value) {
    if (value instanceof Instant instant) return instant;
    return ((Timestamp) value).toInstant();
  }
}
