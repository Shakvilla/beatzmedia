package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.shakvilla.beatzmedia.identity.application.port.out.PhoneVerificationChallengeRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.PhoneVerificationChallenge;

/** JPA persistence adapter for phone verification challenges. */
@ApplicationScoped
public class JpaPhoneVerificationChallengeRepository implements PhoneVerificationChallengeRepository {
  private final EntityManager em;

  @Inject
  public JpaPhoneVerificationChallengeRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Optional<PhoneVerificationChallenge> findActive(AccountId accountId, Instant now, boolean lock) {
    var query = em.createQuery(
            "SELECT c FROM PhoneVerificationChallengeEntity c WHERE c.accountId = :accountId "
                + "AND c.status = 'sent' AND c.expiresAt > :now ORDER BY c.createdAt DESC",
            PhoneVerificationChallengeEntity.class)
        .setParameter("accountId", accountId.value())
        .setParameter("now", now)
        .setMaxResults(1);
    if (lock) query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
    return query.getResultStream().findFirst().map(PhoneVerificationChallengeMapper::toDomain);
  }

  @Override
  public Optional<PhoneVerificationChallenge> findById(String id) {
    return Optional.ofNullable(em.find(PhoneVerificationChallengeEntity.class, id))
        .map(PhoneVerificationChallengeMapper::toDomain);
  }

  @Override
  public Optional<PhoneVerificationChallenge> findLatestForAccount(AccountId accountId) {
    return em.createQuery(
            "SELECT c FROM PhoneVerificationChallengeEntity c WHERE c.accountId = :accountId "
                + "ORDER BY c.createdAt DESC",
            PhoneVerificationChallengeEntity.class)
        .setParameter("accountId", accountId.value())
        .setMaxResults(1)
        .getResultStream()
        .findFirst()
        .map(PhoneVerificationChallengeMapper::toDomain);
  }

  @Override
  public long countCreatedForAccountSince(AccountId accountId, Instant since) {
    return em.createQuery(
            "SELECT COUNT(c) FROM PhoneVerificationChallengeEntity c "
                + "WHERE c.accountId = :accountId AND c.createdAt >= :since",
            Long.class)
        .setParameter("accountId", accountId.value())
        .setParameter("since", since)
        .getSingleResult();
  }

  @Override
  public long countCreatedForPhoneSince(String phone, Instant since) {
    return em.createQuery(
            "SELECT COUNT(c) FROM PhoneVerificationChallengeEntity c "
                + "WHERE c.phone = :phone AND c.createdAt >= :since",
            Long.class)
        .setParameter("phone", phone)
        .setParameter("since", since)
        .getSingleResult();
  }

  @Override
  public void invalidateActive(AccountId accountId) {
    em.createQuery(
            "UPDATE PhoneVerificationChallengeEntity c SET c.status = 'expired' "
                + "WHERE c.accountId = :accountId AND c.status IN ('pending','sent')")
        .setParameter("accountId", accountId.value())
        .executeUpdate();
  }

  @Override
  public PhoneVerificationChallenge save(PhoneVerificationChallenge challenge) {
    PhoneVerificationChallengeEntity entity = PhoneVerificationChallengeMapper.toEntity(challenge);
    PhoneVerificationChallengeEntity existing = em.find(PhoneVerificationChallengeEntity.class, entity.id);
    if (existing == null) em.persist(entity);
    else em.merge(entity);
    return challenge;
  }

  @Override
  public void deleteCreatedBefore(Instant before) {
    em.createQuery("DELETE FROM PhoneVerificationChallengeEntity c WHERE c.createdAt < :before")
        .setParameter("before", before)
        .executeUpdate();
  }
}
