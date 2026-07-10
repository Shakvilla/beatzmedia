package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;
import org.shakvilla.beatzmedia.admin.domain.UserFilter;
import org.shakvilla.beatzmedia.identity.adapter.out.persistence.AccountEntity;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

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

  // ---- WU-ADM-2 additions: paged/filtered user list, single-user lookup, global counts ----

  @Override
  public Page<AccountRow> listUsers(String q, UserFilter filter, PageRequest page) {
    String where = buildWhereClause(filter, q);

    TypedQuery<Long> countQuery =
        em.createQuery("SELECT COUNT(a) FROM AccountEntity a" + where, Long.class);
    applyParams(countQuery, q);
    long total = countQuery.getSingleResult();

    if (total == 0) {
      return Page.empty(page.page(), page.size());
    }

    TypedQuery<AccountEntity> dataQuery = em.createQuery(
        "SELECT a FROM AccountEntity a" + where + " ORDER BY a.createdAt DESC, a.id DESC",
        AccountEntity.class);
    applyParams(dataQuery, q);
    dataQuery.setFirstResult(page.offset());
    dataQuery.setMaxResults(page.size());

    List<AccountRow> items = dataQuery.getResultList().stream()
        .map(IdentityReaderAdapter::toRow)
        .toList();

    return Page.of(items, page.page(), page.size(), total);
  }

  @Override
  public Optional<AccountRow> findUser(String accountId) {
    if (accountId == null || accountId.isBlank()) {
      return Optional.empty();
    }
    AccountEntity entity = em.find(AccountEntity.class, accountId);
    return Optional.ofNullable(entity).map(IdentityReaderAdapter::toRow);
  }

  @Override
  public UserCounts countUsers() {
    Object[] row = (Object[]) em.createNativeQuery(
            "SELECT COUNT(*), "
                + "COUNT(*) FILTER (WHERE is_artist = false), "
                + "COUNT(*) FILTER (WHERE is_artist = true), "
                + "COUNT(*) FILTER (WHERE verified = true), "
                + "COUNT(*) FILTER (WHERE status = 'suspended') "
                + "FROM account")
        .getSingleResult();
    return new UserCounts(
        ((Number) row[0]).intValue(),
        ((Number) row[1]).intValue(),
        ((Number) row[2]).intValue(),
        ((Number) row[3]).intValue(),
        ((Number) row[4]).intValue());
  }

  private static String buildWhereClause(UserFilter filter, String q) {
    StringBuilder sb = new StringBuilder(" WHERE 1=1");
    if (filter != null) {
      switch (filter) {
        case FANS -> sb.append(" AND a.isArtist = false");
        case ARTISTS -> sb.append(" AND a.isArtist = true");
        case VERIFIED -> sb.append(" AND a.verified = true");
        case SUSPENDED -> sb.append(" AND a.status = 'suspended'");
      }
    }
    if (q != null && !q.isBlank()) {
      sb.append(" AND (LOWER(a.name) LIKE :q OR LOWER(a.email) LIKE :q)");
    }
    return sb.toString();
  }

  private static <T> void applyParams(TypedQuery<T> query, String q) {
    if (q != null && !q.isBlank()) {
      query.setParameter("q", "%" + q.toLowerCase(java.util.Locale.ROOT) + "%");
    }
  }

  private static AccountRow toRow(AccountEntity e) {
    return new AccountRow(
        e.id, e.name, e.email, e.isArtist, e.verified, e.status, e.createdAt, e.updatedAt);
  }
}
