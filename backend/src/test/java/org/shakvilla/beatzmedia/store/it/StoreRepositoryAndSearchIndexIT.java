package org.shakvilla.beatzmedia.store.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.store.application.port.out.SearchIndex;
import org.shakvilla.beatzmedia.store.application.port.out.StoreRepository;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreItemType;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration coverage for the outbound adapters: {@code StoreRepositoryJpa.decrementStock}'s
 * INV-STORE-C floor (atomic conditional UPDATE, never negative) and {@code SearchIndexPg} as a
 * thin adapter over the shared {@code search} module (index/query/remove round trip). Store ADD
 * §5.2 / §9.
 */
@QuarkusTest
@Tag("integration")
class StoreRepositoryAndSearchIndexIT {

  @Inject EntityManager em;
  @Inject StoreRepository storeRepository;
  @Inject SearchIndex searchIndex;

  private String exclusiveId;

  @BeforeEach
  @Transactional
  void seed() {
    exclusiveId = "it-decrement-" + System.nanoTime();
    em.createNativeQuery(
            "INSERT INTO store_item (id, type, title, artist_name, image, price_minor, currency,"
                + " badges, popularity, created_at, drops_at, stock_remaining) VALUES (:id,"
                + " 'EXCLUSIVE', 'Decrement Test', 'Artist', 'img.png', 80000, 'GHS', '[]'::jsonb,"
                + " 90, now(), now() + interval '5 days', 2) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", exclusiveId)
        .executeUpdate();
  }

  @Test
  @Transactional
  void decrementStock_floorGuardedAtZero_neverGoesNegative() {
    StoreItemId id = new StoreItemId(exclusiveId);

    storeRepository.decrementStock(id, 1);
    em.flush();
    em.clear();
    assertEquals(1, storeRepository.findById(id).orElseThrow().stockRemaining().orElseThrow());

    // More than remaining: the atomic conditional UPDATE (WHERE stock_remaining >= qty) rejects
    // the whole decrement outright rather than partially applying/clamping it — INV-STORE-C never
    // goes negative, and an over-decrement is a silent no-op (stock stays at 1).
    storeRepository.decrementStock(id, 5);
    em.flush();
    em.clear();
    assertEquals(1, storeRepository.findById(id).orElseThrow().stockRemaining().orElseThrow());

    storeRepository.decrementStock(id, 1); // exactly the remaining unit — floor reached
    em.flush();
    em.clear();
    assertEquals(0, storeRepository.findById(id).orElseThrow().stockRemaining().orElseThrow());

    storeRepository.decrementStock(id, 1); // already at floor — silent no-op
    em.flush();
    em.clear();
    assertEquals(0, storeRepository.findById(id).orElseThrow().stockRemaining().orElseThrow());
  }

  @Test
  @Transactional
  void searchIndex_indexThenQuery_thenRemove_roundTrips() {
    StoreItem item = storeRepository.findById(new StoreItemId(exclusiveId)).orElseThrow();

    searchIndex.index(item);

    var page =
        searchIndex.query(
            "Decrement Test", Optional.empty(), Optional.empty(),
            org.shakvilla.beatzmedia.store.domain.StoreSort.POPULAR, PageRequest.defaults());
    assertTrue(page.items().stream().anyMatch(id -> id.value().equals(exclusiveId)));

    searchIndex.remove(new StoreItemId(exclusiveId));
    var afterRemove =
        searchIndex.query(
            "Decrement Test", Optional.of(StoreItemType.EXCLUSIVE), Optional.empty(),
            org.shakvilla.beatzmedia.store.domain.StoreSort.POPULAR, PageRequest.defaults());
    assertTrue(afterRemove.items().stream().noneMatch(id -> id.value().equals(exclusiveId)));
  }
}
