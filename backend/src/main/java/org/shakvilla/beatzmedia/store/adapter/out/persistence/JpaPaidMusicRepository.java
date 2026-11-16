package org.shakvilla.beatzmedia.store.adapter.out.persistence;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.store.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.store.application.port.in.PaidMusicView;
import org.shakvilla.beatzmedia.store.application.port.out.PaidMusicRepository;
import org.shakvilla.beatzmedia.store.domain.StoreSort;

/** Reads sellable music from the catalog tables without copying it into store_item. */
@ApplicationScoped
public class JpaPaidMusicRepository implements PaidMusicRepository {
  private static final String MUSIC_SQL = """
      WITH music AS (
        SELECT t.id, 'track' AS kind, t.title, t.artist_name, t.artist_id, t.image,
               t.price_minor, r.genre, t.plays AS popularity,
               COALESCE(r.went_live_at, r.created_at) AS released_at
          FROM release r
          JOIN release_track rt ON rt.release_id = r.id
          JOIN track t ON t.id = rt.track_id
         WHERE r.status = 'live' AND r.visibility = 'public'
           AND t.status = 'ready' AND t.ownership = 'for-sale'
           AND t.price_minor > 0 AND rt.price_minor > 0
        UNION ALL
        SELECT a.id, 'album' AS kind, a.title, a.artist_name, a.artist_id, a.cover_image,
               a.list_price_minor, r.genre,
               (SELECT COALESCE(SUM(t2.plays), 0) FROM release_track rt2
                 JOIN track t2 ON t2.id = rt2.track_id WHERE rt2.release_id = r.id) AS popularity,
               COALESCE(r.went_live_at, r.created_at) AS released_at
          FROM release r
          JOIN album a ON a.id = r.id
         WHERE r.status = 'live' AND r.visibility = 'public' AND r.type <> 'single'
           AND r.list_price_minor > 0 AND a.list_price_minor > 0
      )
      """;

  private final EntityManager em;

  @Inject
  public JpaPaidMusicRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Page<PaidMusicView> find(StoreSort sort, PageRequest page) {
    long total = ((Number) em.createNativeQuery(MUSIC_SQL + "SELECT COUNT(*) FROM music")
        .getSingleResult()).longValue();
    Query query = em.createNativeQuery(MUSIC_SQL
        + "SELECT id, kind, title, artist_name, artist_id, image, price_minor, genre,"
        + " popularity, released_at FROM music ORDER BY " + orderBy(sort)
        + " LIMIT :limit OFFSET :offset");
    query.setParameter("limit", page.size());
    query.setParameter("offset", page.offset());
    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();
    List<PaidMusicView> items = rows.stream().map(this::toView).toList();
    return Page.of(items, page.page(), page.size(), total);
  }

  private PaidMusicView toView(Object[] row) {
    return new PaidMusicView(
        (String) row[0], (String) row[1], (String) row[2], (String) row[3],
        (String) row[4], (String) row[5],
        MoneyView.ofMinor(((Number) row[6]).longValue(), "GHS"),
        (String) row[7], ((Number) row[8]).longValue(), row[9].toString());
  }

  private static String orderBy(StoreSort sort) {
    return switch (sort) {
      case POPULAR -> "popularity DESC, kind, id";
      case NEWEST -> "released_at DESC, kind, id";
      case PRICE_ASC -> "price_minor ASC, kind, id";
      case PRICE_DESC -> "price_minor DESC, kind, id";
    };
  }
}
