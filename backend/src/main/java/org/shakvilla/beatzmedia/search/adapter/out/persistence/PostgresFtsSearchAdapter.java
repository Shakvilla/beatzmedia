package org.shakvilla.beatzmedia.search.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.search.application.port.out.IndexDocumentRepository;
import org.shakvilla.beatzmedia.search.application.port.out.SearchIndex;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.SearchFilters;
import org.shakvilla.beatzmedia.search.domain.SearchHit;
import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;
import org.shakvilla.beatzmedia.search.domain.SearchScope;
import org.shakvilla.beatzmedia.search.domain.Sort;

/**
 * Postgres FTS + pg_trgm implementation of {@link SearchIndex} and {@link IndexDocumentRepository}.
 * <p>
 * Upsert uses {@code INSERT ... ON CONFLICT (entity_type, entity_id) DO UPDATE} (INV-SRCH-1).
 * tsv is maintained by DB trigger V802 (INV-SRCH-4). Search combines websearch_to_tsquery FTS
 * with pg_trgm similarity fallback, filtered by {@code visible=true} (INV-SRCH-2), ranked by
 * ts_rank_cd then popularity.
 * <p>
 * OQ-12: an OpenSearch adapter would implement the same {@link SearchIndex} interface; no other
 * module references this class directly.
 */
@ApplicationScoped
class PostgresFtsSearchAdapter implements SearchIndex, IndexDocumentRepository {

  private final EntityManager em;
  private final Clock clock;
  private final SearchDocumentMapper mapper;

  @Inject
  PostgresFtsSearchAdapter(EntityManager em, Clock clock, SearchDocumentMapper mapper) {
    this.em = em;
    this.clock = clock;
    this.mapper = mapper;
  }

  // -------------------------------------------------------------------------
  // SearchIndex — write
  // -------------------------------------------------------------------------

  @Override
  public void upsert(IndexDocument document) {
    Instant now = clock.now();
    // Use native SQL for upsert with ON CONFLICT; tsv is filled by the DB trigger.
    em.createNativeQuery(
            """
            INSERT INTO search_document
                (entity_type, entity_id, title, subtitle, search_text, popularity, visible, payload, indexed_at)
            VALUES
                (:entityType, :entityId, :title, :subtitle, :searchText, :popularity, :visible, CAST(:payload AS jsonb), :indexedAt)
            ON CONFLICT (entity_type, entity_id) DO UPDATE SET
                title       = EXCLUDED.title,
                subtitle    = EXCLUDED.subtitle,
                search_text = EXCLUDED.search_text,
                popularity  = EXCLUDED.popularity,
                visible     = EXCLUDED.visible,
                payload     = EXCLUDED.payload,
                indexed_at  = EXCLUDED.indexed_at
            """)
        .setParameter("entityType", document.entityType().name())
        .setParameter("entityId", document.entityId())
        .setParameter("title", document.title())
        .setParameter("subtitle", document.subtitle())
        .setParameter("searchText", document.searchText())
        .setParameter("popularity", document.popularity().score())
        .setParameter("visible", document.visible())
        .setParameter("payload", payloadJson(document.payload()))
        .setParameter("indexedAt", now)
        .executeUpdate();
  }

  @Override
  public void remove(EntityType type, String entityId) {
    em.createNativeQuery(
            "DELETE FROM search_document WHERE entity_type = :type AND entity_id = :id")
        .setParameter("type", type.name())
        .setParameter("id", entityId)
        .executeUpdate();
  }

  // -------------------------------------------------------------------------
  // SearchIndex — read
  // -------------------------------------------------------------------------

  @Override
  @SuppressWarnings("unchecked")
  public SearchResults search(SearchQuery query, SearchFilters filters, PageRequest page) {
    String q = query.q();
    SearchScope scope = query.scope();
    Sort sort = filters.sort();

    // Build entity_type filter clause
    String typeClause = buildTypeClause(scope);

    // Determine ORDER BY
    String orderBy = buildOrderBy(sort);

    // Combined FTS + trgm query: match tsv OR title similarity
    String sql =
        """
        SELECT
            entity_type,
            entity_id,
            title,
            subtitle,
            payload::text,
            popularity,
            COALESCE(ts_rank_cd(tsv, websearch_to_tsquery('simple', :q)), 0.0) AS rank
        FROM search_document
        WHERE visible = true
          AND (
              tsv @@ websearch_to_tsquery('simple', :q)
              OR title %% :q
          )
        """
            + typeClause
            + " ORDER BY "
            + orderBy
            + " LIMIT :limit OFFSET :offset";

    List<Object[]> rows =
        em.createNativeQuery(sql)
            .setParameter("q", q)
            .setParameter("limit", page.size())
            .setParameter("offset", page.offset())
            .getResultList();

    // Count query
    String countSql =
        """
        SELECT COUNT(*)
        FROM search_document
        WHERE visible = true
          AND (
              tsv @@ websearch_to_tsquery('simple', :q)
              OR title %% :q
          )
        """
            + typeClause;

    long total =
        ((Number)
                em.createNativeQuery(countSql)
                    .setParameter("q", q)
                    .getSingleResult())
            .longValue();

    // Group hits
    List<SearchHit> tracks = new ArrayList<>();
    List<SearchHit> artists = new ArrayList<>();
    List<SearchHit> albums = new ArrayList<>();
    List<SearchHit> playlists = new ArrayList<>();
    List<SearchHit> storeItems = new ArrayList<>();
    List<SearchHit> podcasts = new ArrayList<>();
    List<SearchHit> events = new ArrayList<>();
    List<SearchHit> allHits = new ArrayList<>();

    for (Object[] row : rows) {
      String entityType = (String) row[0];
      String entityId = (String) row[1];
      String title = (String) row[2];
      String subtitle = (String) row[3];
      String payload = (String) row[4];
      long popularity = ((Number) row[5]).longValue();
      double score = ((Number) row[6]).doubleValue();

      SearchHit hit =
          mapper.toHitFromRow(entityType, entityId, title, subtitle, payload, popularity, score);
      allHits.add(hit);

      switch (EntityType.valueOf(entityType)) {
        case TRACK -> tracks.add(hit);
        case ARTIST -> artists.add(hit);
        case ALBUM -> albums.add(hit);
        case PLAYLIST -> playlists.add(hit);
        case STORE_ITEM -> storeItems.add(hit);
        case PODCAST -> podcasts.add(hit);
        case EVENT -> events.add(hit);
      }
    }

    Optional<SearchHit> topResult = SearchResults.computeTopResult(allHits);

    return new SearchResults(
        tracks, artists, albums, playlists, storeItems, podcasts, events, topResult, total);
  }

  // -------------------------------------------------------------------------
  // IndexDocumentRepository
  // -------------------------------------------------------------------------

  @Override
  public long count(EntityType type) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM search_document WHERE entity_type = :type")
                .setParameter("type", type.name())
                .getSingleResult())
        .longValue();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private String buildTypeClause(SearchScope scope) {
    if (scope == null || scope == SearchScope.ALL) return "";
    return " AND entity_type = '" + scope.name() + "'";
  }

  private String buildOrderBy(Sort sort) {
    if (sort == null) return "rank DESC, popularity DESC";
    return switch (sort) {
      case RELEVANCE -> "rank DESC, popularity DESC";
      case POPULAR -> "popularity DESC, rank DESC";
      case NEWEST -> "indexed_at DESC, rank DESC";
      case PRICE_ASC ->
          "CAST(payload->>'price_minor' AS BIGINT) ASC NULLS LAST, rank DESC";
      case PRICE_DESC ->
          "CAST(payload->>'price_minor' AS BIGINT) DESC NULLS LAST, rank DESC";
    };
  }

  private String payloadJson(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) return "{}";
    try {
      return new ObjectMapper().writeValueAsString(payload);
    } catch (Exception e) {
      return "{}";
    }
  }
}
