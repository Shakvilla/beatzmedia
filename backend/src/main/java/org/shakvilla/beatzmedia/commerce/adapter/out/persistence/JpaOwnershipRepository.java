package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.commerce.application.port.out.OwnershipRepository;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OwnershipGrant;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * JPA implementation of {@link OwnershipRepository}. Reads/writes only {@code ownership_grant} +
 * {@code order_grant_posting}. Commerce ADD §5.2.
 *
 * <p>{@link #claimGrantPosting(OrderId)} uses an atomic {@code INSERT … ON CONFLICT DO NOTHING} so a
 * re-delivered settlement is a clean {@code false} (row already claimed) rather than a
 * transaction-poisoning constraint violation — the exactly-once guarantee for the grant fan-out
 * (INV-1), mirroring the payments {@code ledger_posting} claim (WU-PAY-3).
 */
@ApplicationScoped
public class JpaOwnershipRepository implements OwnershipRepository {

  private final EntityManager em;

  @Inject
  public JpaOwnershipRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean claimGrantPosting(OrderId orderId) {
    int inserted =
        em.createNativeQuery(
                "INSERT INTO order_grant_posting (order_id, posted_at) VALUES (:oid, now())"
                    + " ON CONFLICT (order_id) DO NOTHING")
            .setParameter("oid", orderId.value())
            .executeUpdate();
    return inserted == 1;
  }

  @Override
  public OwnershipGrant save(OwnershipGrant grant) {
    OwnershipGrantEntity e = new OwnershipGrantEntity();
    e.id = grant.getId();
    e.accountId = grant.getAccountId().value();
    e.trackId = grant.getTrackId();
    e.episodeId = grant.getEpisodeId();
    e.sourceOrderId = grant.getSourceOrderId().value();
    e.grantedAt = grant.getGrantedAt();
    e.revokedAt = grant.getRevokedAt();
    em.persist(e);
    em.flush();
    return grant;
  }

  @Override
  public boolean existsActiveForTrack(AccountId account, String trackId) {
    Long count =
        em.createQuery(
                "SELECT COUNT(g) FROM OwnershipGrantEntity g WHERE g.accountId = :acc"
                    + " AND g.trackId = :tid AND g.revokedAt IS NULL",
                Long.class)
            .setParameter("acc", account.value())
            .setParameter("tid", trackId)
            .getSingleResult();
    return count > 0;
  }

  @Override
  public boolean existsActiveForEpisode(AccountId account, String episodeId) {
    Long count =
        em.createQuery(
                "SELECT COUNT(g) FROM OwnershipGrantEntity g WHERE g.accountId = :acc"
                    + " AND g.episodeId = :eid AND g.revokedAt IS NULL",
                Long.class)
            .setParameter("acc", account.value())
            .setParameter("eid", episodeId)
            .getSingleResult();
    return count > 0;
  }

  @Override
  public List<OwnershipGrant> findBySourceOrder(OrderId orderId) {
    return em
        .createQuery(
            "SELECT g FROM OwnershipGrantEntity g WHERE g.sourceOrderId = :oid",
            OwnershipGrantEntity.class)
        .setParameter("oid", orderId.value())
        .getResultList()
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public OwnershipGrant update(OwnershipGrant grant) {
    OwnershipGrantEntity e = em.find(OwnershipGrantEntity.class, grant.getId());
    if (e != null) {
      e.revokedAt = grant.getRevokedAt();
      em.flush();
    }
    return grant;
  }

  @Override
  public List<String> activeTrackIds(AccountId account) {
    return em
        .createQuery(
            "SELECT g.trackId FROM OwnershipGrantEntity g WHERE g.accountId = :acc"
                + " AND g.trackId IS NOT NULL AND g.revokedAt IS NULL",
            String.class)
        .setParameter("acc", account.value())
        .getResultList();
  }

  private OwnershipGrant toDomain(OwnershipGrantEntity e) {
    return new OwnershipGrant(
        e.id,
        new AccountId(e.accountId),
        e.trackId,
        e.episodeId,
        new OrderId(e.sourceOrderId),
        e.grantedAt,
        e.revokedAt);
  }
}
