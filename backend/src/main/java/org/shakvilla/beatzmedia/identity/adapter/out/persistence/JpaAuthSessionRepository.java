package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.shakvilla.beatzmedia.identity.application.port.out.AuthSessionRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AuthSession;

@ApplicationScoped
public class JpaAuthSessionRepository implements AuthSessionRepository {
  private final EntityManager em;

  @Inject
  public JpaAuthSessionRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void save(AuthSession session) {
    AuthSessionEntity entity = em.find(AuthSessionEntity.class, session.id());
    if (entity == null) {
      entity = new AuthSessionEntity();
      entity.id = session.id();
      entity.accountId = session.accountId().value();
      entity.tokenHash = session.tokenHash();
      entity.createdAt = session.createdAt();
      entity.expiresAt = session.expiresAt();
      entity.lastUsedAt = session.lastUsedAt();
      entity.revokedAt = session.revokedAt();
      em.persist(entity);
    } else {
      entity.lastUsedAt = session.lastUsedAt();
      entity.revokedAt = session.revokedAt();
    }
  }

  @Override
  public Optional<AuthSession> findForUpdate(String id) {
    AuthSessionEntity entity = em.find(AuthSessionEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
    return Optional.ofNullable(entity).map(e -> new AuthSession(
        e.id, new AccountId(e.accountId), e.tokenHash, e.createdAt, e.lastUsedAt,
        e.expiresAt, e.revokedAt));
  }
}
