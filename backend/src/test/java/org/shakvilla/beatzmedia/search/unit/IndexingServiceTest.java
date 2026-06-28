package org.shakvilla.beatzmedia.search.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.search.application.service.IndexingServiceTestHelper;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.Popularity;

/**
 * Unit tests for IndexEntityUseCase via IndexingService.
 * Uses FakeSearchIndex — no framework involved.
 */
class IndexingServiceTest {

  FakeSearchIndex fakeIndex;
  IndexingServiceTestHelper service;

  @BeforeEach
  void setUp() {
    fakeIndex = new FakeSearchIndex();
    service = new IndexingServiceTestHelper(fakeIndex);
  }

  @Test
  void index_upserts_document() {
    var doc = doc("t1", "Shakira Track", EntityType.TRACK);
    service.index(doc);
    assertTrue(fakeIndex.store.containsKey("TRACK|t1"));
    assertEquals(1, fakeIndex.upsertCallCount);
  }

  @Test
  void index_is_idempotent_on_type_and_id() {
    // INV-SRCH-1: second upsert with same (type,id) must overwrite, not duplicate
    var doc1 = doc("t1", "Original Title", EntityType.TRACK);
    var doc2 = new IndexDocument(
        EntityType.TRACK, "t1", "Updated Title", null, "", Popularity.ZERO, true, Map.of());
    service.index(doc1);
    service.index(doc2);

    assertEquals(1, fakeIndex.store.size());
    assertEquals("Updated Title", fakeIndex.store.get("TRACK|t1").title());
    assertEquals(2, fakeIndex.upsertCallCount);
  }

  @Test
  void deindex_removes_document() {
    service.index(doc("t1", "Track", EntityType.TRACK));
    service.deindex(EntityType.TRACK, "t1");
    assertTrue(fakeIndex.store.isEmpty());
    assertEquals(1, fakeIndex.removeCallCount);
  }

  @Test
  void deindex_is_noop_when_document_absent() {
    service.deindex(EntityType.TRACK, "nonexistent");
    assertEquals(1, fakeIndex.removeCallCount); // called through, no exception
  }

  @Test
  void popularity_taken_from_supplied_value_never_recomputed() {
    // INV-SRCH-3
    var pop = new Popularity(9999L);
    var doc = new IndexDocument(EntityType.TRACK, "t2", "Pop Track", null, "", pop, true, Map.of());
    service.index(doc);
    var stored = fakeIndex.store.get("TRACK|t2");
    assertNotNull(stored);
    assertEquals(9999L, stored.popularity().score());
  }

  private IndexDocument doc(String id, String title, EntityType type) {
    return new IndexDocument(type, id, title, null, "", Popularity.ZERO, true, Map.of());
  }
}
