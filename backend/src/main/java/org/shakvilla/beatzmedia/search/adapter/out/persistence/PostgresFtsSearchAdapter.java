package org.shakvilla.beatzmedia.search.adapter.out.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Postgres FTS + pg_trgm implementation of {@link SearchIndex} and {@link IndexDocumentRepository}.
 * <p>
 * Upsert uses {@code INSERT ... ON CONFLICT (entity_type, entity_id) DO UPDATE} (INV-SRCH-1).
 * tsv is maintained by DB trigger V802 (INV-SRCH-4). Search combines websearch_to_tsquery FTS
 * with pg_trgm similarity fallback, filtered by {@code visible=true} (INV-SRCH-2), ranked by
 * ts_rank_cd then popularity.
 * <p>
 * Filters: {@code SearchFilters.type()} and {@code SearchFilters.genre()} are matched against
 * the {@code payload} JSONB field ({@code payload->>'type'} and {@code payload->>'genre'}). Only
 * TRACK/STORE_ITEM payload shapes carry these fields per the allow-list in
 * {@link SearchDocumentMapper}. If a document does not carry the field the filter simply produces
 * no match for that document (SQL {@code payload->>'field' = :value} is NULL-safe via IS NOT
 * DISTINCT FROM or by the caller only setting present filters).
 * <p>
 * OQ-12: an OpenSearch adapter would implement the same {@link SearchIndex} interface; no other
 * module references this class directly.
 */
@ApplicationScoped
class PostgresFtsSearchAdapter implements SearchIndex, IndexDocumentRepository {

  private final EntityManager em;
  private final Clock clock;
  private final SearchDocumentMapper mapper;
  private final ObjectMapper objectMapper;

  @Inject
  PostgresFtsSearchAdapter(EntityManager em, Clock clock, SearchDocumentMapper mapper, ObjectMapper objectMapper) {
    this.em = em;
    this.clock = clock;
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  // -------------------------------------------------------------------------
  // SearchIndex — write
  // -------------------------------------------------------------------------

  @Override
  public void upsert(IndexDocument document) {
    Instant now = clock.now();
    // Apply allow-list before persistence (F4): strip keys not permitted for this entity type.
    var allowedPayload = SearchDocumentMapper.applyAllowList(document.entityType(), document.payload());
    // Extract price_minor from the allow-listed payload for the typed sort column (F2/INV-SRCH money-as-minor-units).
    Long priceMinor = extractPriceMinor(allowedPayload);
    // Use native SQL for upsert with ON CONFLICT; tsv is filled by the DB trigger.
    em.createNativeQuery(
            """
            INSERT INTO search_document
                (entity_type, entity_id, title, subtitle, search_text, popularity, visible, payload, price_minor, indexed_at)
            VALUES
                (:entityType, :entityId, :title, :subtitle, :searchText, :popularity, :visible, CAST(:payload AS jsonb), :priceMinor, :indexedAt)
            ON CONFLICT (entity_type, entity_id) DO UPDATE SET
                title       = EXCLUDED.title,
                subtitle    = EXCLUDED.subtitle,
                search_text = EXCLUDED.search_text,
                popularity  = EXCLUDED.popularity,
                visible     = EXCLUDED.visible,
                payload     = EXCLUDED.payload,
                price_minor = EXCLUDED.price_minor,
                indexed_at  = EXCLUDED.indexed_at
            """)
        .setParameter("entityType", document.entityType().name())
        .setParameter("entityId", document.entityId())
        .setParameter("title", document.title())
        .setParameter("subtitle", document.subtitle())
        .setParameter("searchText", document.searchText())
        .setParameter("popularity", document.popularity().score())
        .setParameter("visible", document.visible())
        .setParameter("payload", payloadJson(allowedPayload))
        .setParameter("priceMinor", priceMinor)
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

    // Build parameterised filter clauses (F1, F5 — no string concatenation of user/enum-derived values).
    // scope -> entity_type bound parameter; type/genre -> payload fields bound parameters.
    boolean hasScope = scope != null && scope != SearchScope.ALL;
    boolean hasType = filters.type().isPresent();
    boolean hasGenre = filters.genre().isPresent();

    String filterClauses =
        (hasScope  ? " AND entity_type = :scope"              : "")
      + (hasType   ? " AND payload->>'type' = :filterType"    : "")
      + (hasGenre  ? " AND payload->>'genre' = :filterGenre"  : "");

    // Determine ORDER BY using the typed price_minor column (F2 — no JSONB cast in ORDER BY).
    String orderBy = buildOrderBy(sort);

    // Combined FTS + trgm query: match tsv OR title similarity.
    // Cast :q to TEXT so pg_trgm % operator resolves (avoids character varying overload issue).
    String sql =
        """
        SELECT
            entity_type,
            entity_id,
            title,
            subtitle,
            payload::text,
            popularity,
            COALESCE(ts_rank_cd(tsv, websearch_to_tsquery('simple', CAST(:q AS text))), 0.0) AS rank
        FROM search_document
        WHERE visible = true
          AND (
              tsv @@ websearch_to_tsquery('simple', CAST(:q AS text))
              OR similarity(title, CAST(:q AS text)) > 0.2
          )
        """
            + filterClauses
            + " ORDER BY "
            + orderBy
            + " LIMIT :limit OFFSET :offset";

    var mainQuery = em.createNativeQuery(sql)
            .setParameter("q", q)
            .setParameter("limit", page.size())
            .setParameter("offset", page.offset());
    if (hasScope)  mainQuery.setParameter("scope", scope.name());
    if (hasType)   mainQuery.setParameter("filterType", filters.type().get());
    if (hasGenre)  mainQuery.setParameter("filterGenre", filters.genre().get());

    List<Object[]> rows = mainQuery.getResultList();

    // Count query
    String countSql =
        """
        SELECT COUNT(*)
        FROM search_document
        WHERE visible = true
          AND (
              tsv @@ websearch_to_tsquery('simple', CAST(:q AS text))
              OR similarity(title, CAST(:q AS text)) > 0.2
          )
        """
            + filterClauses;

    var countQuery = em.createNativeQuery(countSql).setParameter("q", q);
    if (hasScope)  countQuery.setParameter("scope", scope.name());
    if (hasType)   countQuery.setParameter("filterType", filters.type().get());
    if (hasGenre)  countQuery.setParameter("filterGenre", filters.genre().get());

    long total = ((Number) countQuery.getSingleResult()).longValue();

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

  /**
   * ORDER BY fragment. Uses the typed {@code price_minor BIGINT} column (F2) — never casts
   * arbitrary JSONB in ORDER BY, which would throw a Postgres cast error on non-numeric values.
   */
  private String buildOrderBy(Sort sort) {
    if (sort == null) return "rank DESC, popularity DESC";
    return switch (sort) {
      case RELEVANCE -> "rank DESC, popularity DESC";
      case POPULAR   -> "popularity DESC, rank DESC";
      case NEWEST    -> "indexed_at DESC, rank DESC";
      case PRICE_ASC  -> "price_minor ASC NULLS LAST, rank DESC";
      case PRICE_DESC -> "price_minor DESC NULLS LAST, rank DESC";
    };
  }

  /**
   * Serialises the allow-listed payload map using the INJECTED ObjectMapper (F3).
   * Throws {@link IllegalStateException} on failure so data loss is never silently swallowed.
   */
  private String payloadJson(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) return "{}";
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialise search document payload", e);
    }
  }

  /**
   * Extracts {@code price_minor} from the payload map for the typed sort column.
   * Returns {@code null} (mapped to SQL NULL) when the field is absent or not a Number.
   */
  private Long extractPriceMinor(Map<String, Object> payload) {
    if (payload == null) return null;
    Object val = payload.get("price_minor");
    if (val instanceof Number n) return n.longValue();
    return null;
  }
}
