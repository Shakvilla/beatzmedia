package org.shakvilla.beatzmedia.search.adapter.out.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.Popularity;
import org.shakvilla.beatzmedia.search.domain.SearchHit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps between {@link IndexDocument}/{@link SearchHit} and {@link SearchDocumentEntity} (ADD §5.2).
 *
 * <h3>Payload allow-list (F4 — security)</h3>
 * {@code search_document.payload} is returned on every public search hit. To prevent future
 * callers from accidentally persisting PII or internal fields, only the keys listed in
 * {@link #ALLOWED_PAYLOAD_KEYS} per {@link EntityType} are written; all other keys are silently
 * dropped before persistence. Callers that need additional fields in the public payload must
 * extend the allow-list here AND update the ADD §6 payload spec in the same PR.
 * <p>
 * Common allowed keys (used across multiple types):
 * {@code image, price_minor, price_amount, price_currency, type, genre}.
 * Type-specific additions are listed in the per-type sets below.
 */
@ApplicationScoped
class SearchDocumentMapper {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  /**
   * Closed allow-list of payload keys per EntityType.
   * Keys not present here are stripped before the document is persisted (F4).
   */
  static final Map<EntityType, Set<String>> ALLOWED_PAYLOAD_KEYS = Map.of(
      EntityType.TRACK,      Set.of("image", "duration_sec", "price_minor", "price_amount", "price_currency", "quality", "type", "genre"),
      EntityType.ARTIST,     Set.of("image", "genre"),
      EntityType.ALBUM,      Set.of("image", "price_minor", "price_amount", "price_currency", "genre"),
      EntityType.PLAYLIST,   Set.of("image"),
      EntityType.STORE_ITEM, Set.of("image", "duration_sec", "price_minor", "price_amount", "price_currency", "quality", "type", "genre"),
      EntityType.PODCAST,    Set.of("image", "genre"),
      EntityType.EVENT,      Set.of("image", "genre", "type")
  );

  private final ObjectMapper objectMapper;

  @Inject
  SearchDocumentMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  SearchDocumentEntity toEntity(IndexDocument doc, java.time.Instant now) {
    var entity = new SearchDocumentEntity();
    entity.entityType = doc.entityType().name();
    entity.entityId = doc.entityId();
    entity.title = doc.title();
    entity.subtitle = doc.subtitle();
    entity.searchText = doc.searchText();
    entity.popularity = doc.popularity().score();
    entity.visible = doc.visible();
    entity.payload = toJson(applyAllowList(doc.entityType(), doc.payload()));
    entity.indexedAt = now;
    return entity;
  }

  SearchHit toHit(SearchDocumentEntity entity, double score) {
    return new SearchHit(
        EntityType.valueOf(entity.entityType),
        entity.entityId,
        entity.title,
        entity.subtitle,
        fromJson(entity.payload),
        score,
        entity.popularity);
  }

  /** Build a hit from a raw native query result row. */
  SearchHit toHitFromRow(
      String entityType,
      String entityId,
      String title,
      String subtitle,
      String payload,
      long popularity,
      double score) {
    return new SearchHit(
        EntityType.valueOf(entityType),
        entityId,
        title,
        subtitle,
        fromJson(payload),
        score,
        popularity);
  }

  /**
   * Applies the closed allow-list for the given entity type, returning a new map containing only
   * permitted keys. Unknown keys are silently dropped (F4).
   */
  static Map<String, Object> applyAllowList(EntityType type, Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) return Map.of();
    Set<String> allowed = ALLOWED_PAYLOAD_KEYS.getOrDefault(type, Set.of());
    Map<String, Object> filtered = new HashMap<>();
    for (var entry : payload.entrySet()) {
      if (allowed.contains(entry.getKey())) {
        filtered.put(entry.getKey(), entry.getValue());
      }
    }
    return filtered;
  }

  private String toJson(Map<String, Object> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> fromJson(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }

  IndexDocument toDomain(SearchDocumentEntity entity) {
    return new IndexDocument(
        EntityType.valueOf(entity.entityType),
        entity.entityId,
        entity.title,
        entity.subtitle,
        entity.searchText,
        new Popularity(entity.popularity),
        entity.visible,
        fromJson(entity.payload));
  }
}
