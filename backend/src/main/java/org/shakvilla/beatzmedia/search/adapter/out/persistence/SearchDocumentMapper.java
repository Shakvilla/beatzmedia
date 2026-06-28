package org.shakvilla.beatzmedia.search.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.Popularity;
import org.shakvilla.beatzmedia.search.domain.SearchHit;

/**
 * Maps between {@link IndexDocument}/{@link SearchHit} and {@link SearchDocumentEntity} (ADD §5.2).
 */
@ApplicationScoped
class SearchDocumentMapper {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

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
    entity.payload = toJson(doc.payload());
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
