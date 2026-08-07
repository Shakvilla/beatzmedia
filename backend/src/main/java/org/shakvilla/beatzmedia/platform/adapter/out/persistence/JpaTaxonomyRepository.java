package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

import org.shakvilla.beatzmedia.platform.application.port.out.TaxonomyRepository;
import org.shakvilla.beatzmedia.platform.domain.TaxonomyKind;
import org.shakvilla.beatzmedia.platform.domain.TaxonomyTerm;

/**
 * JPA adapter for the taxonomy lists.
 *
 * <p><strong>The usage queries are native and cross-module by design.</strong> {@code countUsages}
 * and {@code repointUsages} touch {@code release}, {@code podcast}, {@code studio_podcast_show},
 * {@code event} and {@code store_item} — tables owned by other modules. That is normally forbidden,
 * and it is admissible here only because the platform kernel owns the taxonomy those columns draw
 * their permitted values from: before V972 the same constraint was enforced by Postgres CHECK
 * clauses on those very tables. The queries carry no business logic — a COUNT and an UPDATE of a
 * single text column — and no other module's rows are interpreted.
 */
@ApplicationScoped
public class JpaTaxonomyRepository implements TaxonomyRepository {

  private final EntityManager em;

  @Inject
  public JpaTaxonomyRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<TaxonomyTerm> listAll(TaxonomyKind kind) {
    return em.createQuery(
            "SELECT t FROM TaxonomyTermEntity t WHERE t.kind = :kind"
                + " ORDER BY t.sortOrder, t.label",
            TaxonomyTermEntity.class)
        .setParameter("kind", kind.wireValue())
        .getResultList()
        .stream()
        .map(JpaTaxonomyRepository::toDomain)
        .toList();
  }

  @Override
  public List<TaxonomyTerm> listActive(TaxonomyKind kind) {
    return em.createQuery(
            "SELECT t FROM TaxonomyTermEntity t WHERE t.kind = :kind AND t.active = true"
                + " ORDER BY t.sortOrder, t.label",
            TaxonomyTermEntity.class)
        .setParameter("kind", kind.wireValue())
        .getResultList()
        .stream()
        .map(JpaTaxonomyRepository::toDomain)
        .toList();
  }

  @Override
  public Optional<TaxonomyTerm> findById(String id) {
    return Optional.ofNullable(em.find(TaxonomyTermEntity.class, id))
        .map(JpaTaxonomyRepository::toDomain);
  }

  @Override
  public Optional<TaxonomyTerm> findByKindAndLabel(TaxonomyKind kind, String label) {
    return single(
        "SELECT t FROM TaxonomyTermEntity t WHERE t.kind = :kind AND t.label = :value",
        kind,
        label);
  }

  @Override
  public Optional<TaxonomyTerm> findByKindAndSlug(TaxonomyKind kind, String slug) {
    return single(
        "SELECT t FROM TaxonomyTermEntity t WHERE t.kind = :kind AND t.slug = :value", kind, slug);
  }

  private Optional<TaxonomyTerm> single(String jpql, TaxonomyKind kind, String value) {
    try {
      return Optional.of(
          toDomain(
              em.createQuery(jpql, TaxonomyTermEntity.class)
                  .setParameter("kind", kind.wireValue())
                  .setParameter("value", value)
                  .getSingleResult()));
    } catch (NoResultException e) {
      return Optional.empty();
    }
  }

  @Override
  public void save(TaxonomyTerm term) {
    TaxonomyTermEntity e = new TaxonomyTermEntity();
    e.id = term.id();
    e.kind = term.kind().wireValue();
    e.slug = term.slug();
    e.label = term.label();
    e.colorClass = term.colorClass();
    e.sortOrder = term.sortOrder();
    e.active = term.active();
    // merge, not persist: update() reads the term and writes it back inside one persistence
    // context, so persist would hit NonUniqueObjectException on an already-managed row.
    em.merge(e);
  }

  @Override
  public void delete(String id) {
    TaxonomyTermEntity e = em.find(TaxonomyTermEntity.class, id);
    if (e != null) {
      em.remove(e);
    }
  }

  @Override
  public long countUsages(TaxonomyKind kind, String label) {
    long total = 0;
    for (String sql : usageSql(kind, "SELECT count(*) FROM %s WHERE %s = :label")) {
      Object result = em.createNativeQuery(sql).setParameter("label", label).getSingleResult();
      total += ((Number) result).longValue();
    }
    return total;
  }

  @Override
  public int repointUsages(TaxonomyKind kind, String oldLabel, String newLabel) {
    int rows = 0;
    for (String sql : usageSql(kind, "UPDATE %s SET %s = :newLabel WHERE %s = :oldLabel")) {
      rows +=
          em.createNativeQuery(sql)
              .setParameter("newLabel", newLabel)
              .setParameter("oldLabel", oldLabel)
              .executeUpdate();
    }
    return rows;
  }

  /**
   * The (table, column) pairs each kind is referenced from, rendered into the given template.
   *
   * <p>The template placeholders are filled from this hardcoded switch only — never from request
   * input — so no caller can influence the SQL text. The label itself is always a bound parameter.
   */
  private static List<String> usageSql(TaxonomyKind kind, String template) {
    List<UsageTarget> targets =
        switch (kind) {
          case GENRE -> List.of(new UsageTarget("release", "genre"), new UsageTarget("store_item", "genre"));
          case PODCAST_CATEGORY ->
              List.of(
                  new UsageTarget("podcast", "category"),
                  new UsageTarget("studio_podcast_show", "category"));
          case EVENT_CATEGORY -> List.of(new UsageTarget("event", "category"));
          // Browse tiles are curated rows in this table itself; nothing else references them.
          case BROWSE_CATEGORY -> List.<UsageTarget>of();
        };
    return targets.stream()
        .map(
            t ->
                template.contains("UPDATE")
                    ? String.format(template, quote(t.table()), t.column(), t.column())
                    : String.format(template, quote(t.table()), t.column()))
        .toList();
  }

  /** A (table, column) pair that references a taxonomy label. Never built from request input. */
  private record UsageTarget(String table, String column) {}

  /** {@code release} and {@code event} are reserved words in Postgres; always quote table names. */
  private static String quote(String table) {
    return "\"" + table + "\"";
  }

  private static TaxonomyTerm toDomain(TaxonomyTermEntity e) {
    return new TaxonomyTerm(
        e.id,
        TaxonomyKind.fromWireValue(e.kind),
        e.slug,
        e.label,
        e.colorClass,
        e.sortOrder,
        e.active);
  }
}
