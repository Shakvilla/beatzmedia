package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;
import org.shakvilla.beatzmedia.identity.adapter.out.persistence.AccountEntity;

/**
 * Implements the admin module's {@link IdentityReader} port by querying the identity module's
 * {@code account} JPA entity in-process. No cross-module FK; uses the shared {@link
 * EntityManager} targeting the same schema (mirrors {@code library.CatalogReaderAdapter}). Admin
 * ADD §4.3 (identity reader) — {@code requesterRef} resolves to a display name here, never via a
 * direct table join in application code.
 */
@ApplicationScoped
public class IdentityReaderAdapter implements IdentityReader {

  private final EntityManager em;

  @Inject
  public IdentityReaderAdapter(EntityManager em) {
    this.em = em;
  }

  @Override
  public Optional<String> displayNameOf(String accountId) {
    if (accountId == null || accountId.isBlank()) {
      return Optional.empty();
    }
    AccountEntity entity = em.find(AccountEntity.class, accountId);
    return Optional.ofNullable(entity).map(e -> e.name);
  }

  @Override
  public int countActiveAccounts() {
    Long count = em.createQuery(
            "SELECT COUNT(a) FROM AccountEntity a WHERE a.status = :status", Long.class)
        .setParameter("status", "active")
        .getSingleResult();
    return count.intValue();
  }

  @Override
  public int countNewArtists(Instant since) {
    Long count = em.createQuery(
            "SELECT COUNT(a) FROM AccountEntity a WHERE a.isArtist = true AND a.createdAt >= :since",
            Long.class)
        .setParameter("since", since)
        .getSingleResult();
    return count.intValue();
  }
}
