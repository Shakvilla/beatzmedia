package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader;
import org.shakvilla.beatzmedia.admin.domain.CatalogFilter;
import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.ArtistProfileEntity;
import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.ReleaseEntity;
import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.ReleaseTrackEntity;
import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.SplitEntryEntity;
import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.TrackEntity;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Implements admin's {@link CatalogAdminReader} output port by querying catalog's {@code
 * release}/{@code release_track}/{@code track}/{@code split_entry}/{@code artist_profile} JPA
 * entities in-process. No cross-module FK; uses the shared {@link EntityManager} targeting the
 * same schema — the SAME documented, accepted exception to strict hexagonal purity as {@code
 * IdentityReaderAdapter} (admin ADD §4.3/§13). Read-only: mutations go through {@code
 * CatalogAdminPortAdapter} instead.
 */
@ApplicationScoped
public class CatalogAdminReaderAdapter implements CatalogAdminReader {

  private final EntityManager em;

  @Inject
  public CatalogAdminReaderAdapter(EntityManager em) {
    this.em = em;
  }

  @Override
  public Page<CatalogRow> list(CatalogFilter filter, String q, PageRequest page) {
    List<String> statuses = bucketStatuses(filter);
    boolean hasQ = q != null && !q.isBlank();

    StringBuilder jpql = new StringBuilder("SELECT r FROM ReleaseEntity r WHERE 1=1");
    StringBuilder countJpql = new StringBuilder("SELECT COUNT(r) FROM ReleaseEntity r WHERE 1=1");
    if (statuses != null) {
      jpql.append(" AND r.status IN :statuses");
      countJpql.append(" AND r.status IN :statuses");
    }
    if (hasQ) {
      String cond = " AND (LOWER(r.title) LIKE :q OR EXISTS (SELECT 1 FROM ArtistProfileEntity a "
          + "WHERE a.id = r.artistId AND LOWER(a.name) LIKE :q))";
      jpql.append(cond);
      countJpql.append(cond);
    }
    jpql.append(" ORDER BY r.createdAt DESC, r.id DESC");

    TypedQuery<ReleaseEntity> query = em.createQuery(jpql.toString(), ReleaseEntity.class);
    TypedQuery<Long> countQuery = em.createQuery(countJpql.toString(), Long.class);
    if (statuses != null) {
      query.setParameter("statuses", statuses);
      countQuery.setParameter("statuses", statuses);
    }
    if (hasQ) {
      String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
      query.setParameter("q", like);
      countQuery.setParameter("q", like);
    }

    long total = countQuery.getSingleResult();
    if (total == 0) {
      return Page.empty(page.page(), page.size());
    }

    List<ReleaseEntity> releases =
        query.setFirstResult(page.offset()).setMaxResults(page.size()).getResultList();

    List<String> releaseIds = releases.stream().map(r -> r.id).toList();
    Map<String, Long> trackCounts = trackCountsByRelease(releaseIds);
    Map<String, String> artistNames =
        artistNamesById(releases.stream().map(r -> r.artistId).distinct().toList());

    List<CatalogRow> items = releases.stream()
        .map(r -> new CatalogRow(
            r.id,
            r.title,
            r.artistId,
            artistNames.getOrDefault(r.artistId, r.artistId),
            r.type,
            trackCounts.getOrDefault(r.id, 0L).intValue(),
            r.status))
        .toList();

    return Page.of(items, page.page(), page.size(), total);
  }

  @Override
  public Optional<CatalogDetailRow> detail(String releaseId) {
    ReleaseEntity r = em.find(ReleaseEntity.class, releaseId);
    if (r == null) {
      return Optional.empty();
    }

    List<ReleaseTrackEntity> releaseTracks = em.createQuery(
            "SELECT rt FROM ReleaseTrackEntity rt WHERE rt.pk.releaseId = :rid "
                + "ORDER BY rt.pk.position",
            ReleaseTrackEntity.class)
        .setParameter("rid", releaseId)
        .getResultList();
    List<String> trackIds = releaseTracks.stream().map(rt -> rt.trackId).toList();

    Map<String, TrackEntity> tracksById = trackIds.isEmpty()
        ? Map.of()
        : em.createQuery("SELECT t FROM TrackEntity t WHERE t.id IN :ids", TrackEntity.class)
            .setParameter("ids", trackIds)
            .getResultList()
            .stream()
            .collect(Collectors.toMap(t -> t.id, t -> t));

    List<TrackRow> tracks = releaseTracks.stream()
        .map(rt -> {
          TrackEntity t = tracksById.get(rt.trackId);
          return new TrackRow(
              rt.pk.position,
              rt.trackId,
              t != null ? t.title : rt.trackId,
              t != null ? t.durationSec : 0,
              rt.priceMinor);
        })
        .toList();

    List<SplitRow> splits = trackIds.isEmpty()
        ? List.of()
        : em.createQuery(
                "SELECT s FROM SplitEntryEntity s WHERE s.trackId IN :ids", SplitEntryEntity.class)
            .setParameter("ids", trackIds)
            .getResultList()
            .stream()
            .map(s -> new SplitRow(s.trackId, s.name, s.role, s.percent, s.confirmation))
            .toList();

    String artistName = artistNamesById(List.of(r.artistId)).getOrDefault(r.artistId, r.artistId);

    return Optional.of(new CatalogDetailRow(
        r.id, r.title, r.artistId, artistName, r.type, r.status, r.createdAt, tracks, splits));
  }

  @Override
  public CatalogCounts counts() {
    Object[] row = (Object[]) em.createNativeQuery(
            "SELECT COUNT(*) FILTER (WHERE status IN ('draft', 'in_review')), "
                + "COUNT(*) FILTER (WHERE status IN ('scheduled', 'live')), "
                + "COUNT(*) FILTER (WHERE status = 'takedown') FROM release")
        .getSingleResult();
    return new CatalogCounts(
        ((Number) row[0]).longValue(), ((Number) row[1]).longValue(), ((Number) row[2]).longValue());
  }

  private Map<String, Long> trackCountsByRelease(List<String> releaseIds) {
    if (releaseIds.isEmpty()) {
      return Map.of();
    }
    List<Object[]> rows = em.createQuery(
            "SELECT rt.pk.releaseId, COUNT(rt) FROM ReleaseTrackEntity rt "
                + "WHERE rt.pk.releaseId IN :ids GROUP BY rt.pk.releaseId",
            Object[].class)
        .setParameter("ids", releaseIds)
        .getResultList();
    Map<String, Long> map = new HashMap<>();
    for (Object[] row : rows) {
      map.put((String) row[0], (Long) row[1]);
    }
    return map;
  }

  private Map<String, String> artistNamesById(List<String> artistIds) {
    List<String> ids = artistIds.stream().filter(id -> id != null && !id.isBlank()).toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    return em.createQuery(
            "SELECT a FROM ArtistProfileEntity a WHERE a.id IN :ids", ArtistProfileEntity.class)
        .setParameter("ids", ids)
        .getResultList()
        .stream()
        .collect(Collectors.toMap(a -> a.id, a -> a.name));
  }

  /**
   * Buckets the illustrative {@code pending|published|takedown} wire filter onto real {@code
   * catalog.domain.ReleaseStatus} wire strings. {@code null} filter → no bucketing (all statuses).
   */
  private static List<String> bucketStatuses(CatalogFilter filter) {
    if (filter == null) {
      return null;
    }
    return switch (filter) {
      case PENDING -> List.of("draft", "in_review");
      case PUBLISHED -> List.of("scheduled", "live");
      case TAKEDOWN -> List.of("takedown");
    };
  }
}
