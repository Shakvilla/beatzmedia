package org.shakvilla.beatzmedia.discovery.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.discovery.application.port.out.CollectionSnapshotRepository;

@ApplicationScoped
public class JpaCollectionSnapshotRepository implements CollectionSnapshotRepository {

  private final EntityManager em;

  @Inject
  public JpaCollectionSnapshotRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional
  public void publish(Snapshot snapshot) {
    validate(snapshot);
    SnapshotVersion version = snapshot.version();
    em.createNativeQuery("select pg_advisory_xact_lock(hashtext(:key))")
        .setParameter("key", "discovery:" + version.market() + ":" + version.slug())
        .getSingleResult();
    active(version.market(), version.slug()).ifPresent(current -> {
      if (!version.generatedAt().isAfter(current.generatedAt())) {
        throw new IllegalArgumentException("Collection snapshot is older than the active version");
      }
    });
    em.createNativeQuery("""
        insert into discovery_collection_version
          (id, market, slug, formula_version, generated_at, active)
        values (:id, :market, :slug, :formula, :generated, false)
        """)
        .setParameter("id", version.id())
        .setParameter("market", version.market())
        .setParameter("slug", version.slug())
        .setParameter("formula", version.formulaVersion())
        .setParameter("generated", version.generatedAt())
        .executeUpdate();
    for (Entry entry : snapshot.entries()) {
      em.createNativeQuery("""
          insert into discovery_collection_entry
            (version_id, position, content_kind, content_id, title, subtitle,
             image, route, score, reason_code)
          values (:version, :position, :kind, :content, :title, :subtitle,
                  :image, :route, :score, :reason)
          """)
          .setParameter("version", version.id())
          .setParameter("position", entry.position())
          .setParameter("kind", entry.contentKind())
          .setParameter("content", entry.contentId())
          .setParameter("title", entry.title())
          .setParameter("subtitle", entry.subtitle())
          .setParameter("image", entry.image())
          .setParameter("route", entry.route())
          .setParameter("score", entry.score())
          .setParameter("reason", entry.reasonCode())
          .executeUpdate();
    }
    em.createNativeQuery("""
        update discovery_collection_version set active = false
        where market = :market and slug = :slug and active
        """)
        .setParameter("market", version.market())
        .setParameter("slug", version.slug())
        .executeUpdate();
    em.createNativeQuery("update discovery_collection_version set active = true where id = :id")
        .setParameter("id", version.id())
        .executeUpdate();
  }

  @Override
  public Optional<SnapshotVersion> active(String market, String slug) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows = em.createNativeQuery("""
        select id, market, slug, formula_version, generated_at
        from discovery_collection_version
        where market = :market and slug = :slug and active
        """)
        .setParameter("market", market)
        .setParameter("slug", slug)
        .getResultList();
    if (rows.isEmpty()) return Optional.empty();
    Object[] row = rows.getFirst();
    return Optional.of(new SnapshotVersion(
        (String) row[0], (String) row[1], (String) row[2], (String) row[3], instant(row[4])));
  }

  @Override
  public List<Entry> entries(String versionId, int afterPosition, int limit) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows = em.createNativeQuery("""
        select position, content_kind, content_id, title, subtitle, image, route, score, reason_code
        from discovery_collection_entry
        where version_id = :version and position > :after
        order by position limit :limit
        """)
        .setParameter("version", versionId)
        .setParameter("after", afterPosition)
        .setParameter("limit", limit)
        .getResultList();
    return rows.stream().map(row -> new Entry(
        ((Number) row[0]).intValue(), (String) row[1], (String) row[2],
        (String) row[3], (String) row[4], (String) row[5], (String) row[6],
        ((Number) row[7]).longValue(), (String) row[8])).toList();
  }

  private static void validate(Snapshot snapshot) {
    if (snapshot == null || snapshot.version() == null || snapshot.entries() == null
        || snapshot.entries().isEmpty()) {
      throw new IllegalArgumentException("A nonempty collection snapshot is required");
    }
    SnapshotVersion version = snapshot.version();
    if (blank(version.id()) || blank(version.market()) || blank(version.slug())
        || blank(version.formulaVersion()) || version.generatedAt() == null) {
      throw new IllegalArgumentException("Invalid collection version");
    }
    Set<String> identities = new HashSet<>();
    int expected = 1;
    for (Entry entry : snapshot.entries()) {
      if (entry == null || entry.position() != expected++ || blank(entry.contentKind())
          || blank(entry.contentId()) || blank(entry.title()) || blank(entry.route())
          || blank(entry.reasonCode()) || entry.score() < 0
          || !identities.add(entry.contentKind() + ":" + entry.contentId())) {
        throw new IllegalArgumentException("Collection positions or content identities are invalid");
      }
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static Instant instant(Object value) {
    if (value instanceof Instant instant) return instant;
    if (value instanceof OffsetDateTime offset) return offset.toInstant();
    return ((java.sql.Timestamp) value).toInstant();
  }
}
