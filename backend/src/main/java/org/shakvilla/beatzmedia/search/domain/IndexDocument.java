package org.shakvilla.beatzmedia.search.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Denormalised projection of a source entity; the unit upserted/removed in the search index (ADD §3).
 * Framework-free: no Jakarta/JPA annotations. {@code payload} holds denormalised fields the
 * calling resource needs to reconstruct the frontend type.
 */
public record IndexDocument(
    EntityType entityType,
    String entityId,
    String title,
    String subtitle,
    String searchText,
    Popularity popularity,
    boolean visible,
    Map<String, Object> payload) {

  public IndexDocument {
    Objects.requireNonNull(entityType, "entityType");
    Objects.requireNonNull(entityId, "entityId");
    if (entityId.isBlank()) throw new IllegalArgumentException("entityId must not be blank");
    Objects.requireNonNull(title, "title");
    if (title.isBlank()) throw new IllegalArgumentException("title must not be blank");
    searchText = searchText == null ? "" : searchText;
    Objects.requireNonNull(popularity, "popularity");
    payload = payload == null ? Map.of() : Map.copyOf(payload);
  }

  /** Convenience constructor with subtitle=null. */
  public static IndexDocument of(
      EntityType entityType,
      String entityId,
      String title,
      String searchText,
      Popularity popularity,
      boolean visible,
      Map<String, Object> payload) {
    return new IndexDocument(entityType, entityId, title, null, searchText, popularity, visible, payload);
  }
}
