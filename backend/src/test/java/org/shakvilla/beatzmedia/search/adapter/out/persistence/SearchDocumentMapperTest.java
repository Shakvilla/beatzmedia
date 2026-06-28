package org.shakvilla.beatzmedia.search.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.search.domain.EntityType;

/**
 * Unit tests for {@link SearchDocumentMapper} — payload allow-list enforcement (F4).
 * No framework needed; tests the static helper directly.
 */
class SearchDocumentMapperTest {

  @Test
  void allowed_keys_are_retained_for_track() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("image", "https://cdn.example.com/img.jpg");
    payload.put("duration_sec", 180);
    payload.put("price_minor", 500L);
    payload.put("price_currency", "GHS");
    payload.put("quality", "HIGH");

    Map<String, Object> result = SearchDocumentMapper.applyAllowList(EntityType.TRACK, payload);

    assertTrue(result.containsKey("image"), "image should be retained");
    assertTrue(result.containsKey("duration_sec"), "duration_sec should be retained");
    assertTrue(result.containsKey("price_minor"), "price_minor should be retained");
    assertTrue(result.containsKey("quality"), "quality should be retained");
  }

  @Test
  void disallowed_keys_are_stripped_for_track() {
    // F4: keys not on the allow-list must be dropped before persistence.
    Map<String, Object> payload = new HashMap<>();
    payload.put("image", "https://cdn.example.com/img.jpg");
    payload.put("internal_cost", 999);           // not on allow-list
    payload.put("user_email", "secret@test.com"); // not on allow-list — PII
    payload.put("_debug_flag", true);             // not on allow-list

    Map<String, Object> result = SearchDocumentMapper.applyAllowList(EntityType.TRACK, payload);

    assertFalse(result.containsKey("internal_cost"), "internal_cost must be stripped");
    assertFalse(result.containsKey("user_email"), "PII user_email must be stripped");
    assertFalse(result.containsKey("_debug_flag"), "_debug_flag must be stripped");
    assertTrue(result.containsKey("image"), "image should still be retained");
  }

  @Test
  void disallowed_keys_stripped_for_artist() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("image", "https://cdn.example.com/artist.jpg");
    payload.put("price_minor", 100L);    // not on ARTIST allow-list
    payload.put("internal_id", "xyz");   // not on allow-list

    Map<String, Object> result = SearchDocumentMapper.applyAllowList(EntityType.ARTIST, payload);

    assertTrue(result.containsKey("image"), "image should be retained for ARTIST");
    assertFalse(result.containsKey("price_minor"), "price_minor must be stripped for ARTIST");
    assertFalse(result.containsKey("internal_id"), "internal_id must be stripped");
  }

  @Test
  void empty_payload_returns_empty_map() {
    Map<String, Object> result = SearchDocumentMapper.applyAllowList(EntityType.TRACK, Map.of());
    assertTrue(result.isEmpty());
  }

  @Test
  void null_payload_returns_empty_map() {
    Map<String, Object> result = SearchDocumentMapper.applyAllowList(EntityType.TRACK, null);
    assertTrue(result.isEmpty());
  }

  @Test
  void store_item_retains_type_and_genre_for_filter_support() {
    // F1: type/genre must be in the payload allow-list for STORE_ITEM so the filter
    // (payload->>'type' = :filterType) can match persisted documents.
    Map<String, Object> payload = new HashMap<>();
    payload.put("type", "beat");
    payload.put("genre", "afrobeats");
    payload.put("price_minor", 300L);

    Map<String, Object> result = SearchDocumentMapper.applyAllowList(EntityType.STORE_ITEM, payload);

    assertTrue(result.containsKey("type"), "type must be retained for STORE_ITEM to support filter");
    assertTrue(result.containsKey("genre"), "genre must be retained for STORE_ITEM to support filter");
    assertTrue(result.containsKey("price_minor"), "price_minor must be retained for STORE_ITEM");
  }
}
