package org.shakvilla.beatzmedia.store.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.store.application.port.in.ListStore.StoreQuery;
import org.shakvilla.beatzmedia.store.application.port.in.StoreItemView;
import org.shakvilla.beatzmedia.store.application.service.ListStoreService;
import org.shakvilla.beatzmedia.store.domain.Genre;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreItemType;
import org.shakvilla.beatzmedia.store.domain.StoreSort;
import org.shakvilla.beatzmedia.store.fakes.FakeStoreRepository;

/**
 * Unit tests for {@link ListStoreService} — LLFR-STORE-01.1 (browse, filter, sort, paginate).
 * Sort comparators are exercised against an in-memory fake repository (Store ADD §11).
 */
@Tag("unit")
class ListStoreServiceTest {

  private static StoreItem track(String id, Genre genre, int popularity, String createdAt, long priceMinor) {
    return new StoreItem(
        new StoreItemId(id),
        StoreItemType.TRACK,
        "Title " + id,
        "Artist",
        null,
        "img.png",
        priceMinor,
        Currency.GHS,
        genre,
        List.of(),
        null,
        popularity,
        Instant.parse(createdAt),
        List.of(),
        List.of(),
        "Lossless",
        null,
        null);
  }

  @Test
  void list_noFilter_returnsAllItems() {
    FakeStoreRepository repo =
        new FakeStoreRepository()
            .withItem(track("t1", Genre.AFROBEATS, 90, "2026-05-01T00:00:00Z", 400))
            .withItem(track("t2", Genre.DRILL, 80, "2026-05-02T00:00:00Z", 500));
    ListStoreService service = new ListStoreService(repo);

    Page<StoreItemView> page = service.list(StoreQuery.NONE, PageRequest.defaults());

    assertEquals(2, page.total());
    assertEquals(2, page.items().size());
  }

  @Test
  void list_typeFilter_returnsOnlyMatchingItems() {
    FakeStoreRepository repo = new FakeStoreRepository().withItem(track("t1", Genre.AFROBEATS, 90, "2026-05-01T00:00:00Z", 400));
    ListStoreService service = new ListStoreService(repo);

    Page<StoreItemView> page =
        service.list(
            new StoreQuery(Optional.of(StoreItemType.TRACK), Optional.empty(), StoreSort.POPULAR),
            PageRequest.defaults());

    assertEquals(1, page.total());

    page = service.list(
        new StoreQuery(Optional.of(StoreItemType.MERCH), Optional.empty(), StoreSort.POPULAR),
        PageRequest.defaults());
    assertEquals(0, page.total());
  }

  @Test
  void list_genreFilter_returnsOnlyMatchingItems() {
    FakeStoreRepository repo =
        new FakeStoreRepository()
            .withItem(track("t1", Genre.AFROBEATS, 90, "2026-05-01T00:00:00Z", 400))
            .withItem(track("t2", Genre.DRILL, 80, "2026-05-02T00:00:00Z", 500));
    ListStoreService service = new ListStoreService(repo);

    Page<StoreItemView> page =
        service.list(
            new StoreQuery(Optional.empty(), Optional.of(Genre.DRILL), StoreSort.POPULAR),
            PageRequest.defaults());

    assertEquals(1, page.total());
    assertEquals("t2", page.items().get(0).id());
  }

  @Test
  void list_sortPopular_ordersByPopularityDescending() {
    FakeStoreRepository repo =
        new FakeStoreRepository()
            .withItem(track("low", Genre.AFROBEATS, 10, "2026-05-01T00:00:00Z", 400))
            .withItem(track("high", Genre.AFROBEATS, 99, "2026-05-01T00:00:00Z", 400));
    ListStoreService service = new ListStoreService(repo);

    Page<StoreItemView> page =
        service.list(new StoreQuery(Optional.empty(), Optional.empty(), StoreSort.POPULAR), PageRequest.defaults());

    assertEquals("high", page.items().get(0).id());
    assertEquals("low", page.items().get(1).id());
  }

  @Test
  void list_sortNewest_ordersByCreatedAtDescending() {
    FakeStoreRepository repo =
        new FakeStoreRepository()
            .withItem(track("old", Genre.AFROBEATS, 50, "2026-01-01T00:00:00Z", 400))
            .withItem(track("new", Genre.AFROBEATS, 50, "2026-06-01T00:00:00Z", 400));
    ListStoreService service = new ListStoreService(repo);

    Page<StoreItemView> page =
        service.list(new StoreQuery(Optional.empty(), Optional.empty(), StoreSort.NEWEST), PageRequest.defaults());

    assertEquals("new", page.items().get(0).id());
    assertEquals("old", page.items().get(1).id());
  }

  @Test
  void list_sortPriceAsc_ordersByPriceAscending() {
    FakeStoreRepository repo =
        new FakeStoreRepository()
            .withItem(track("expensive", Genre.AFROBEATS, 50, "2026-01-01T00:00:00Z", 5000))
            .withItem(track("cheap", Genre.AFROBEATS, 50, "2026-01-01T00:00:00Z", 400));
    ListStoreService service = new ListStoreService(repo);

    Page<StoreItemView> page =
        service.list(
            new StoreQuery(Optional.empty(), Optional.empty(), StoreSort.PRICE_ASC), PageRequest.defaults());

    assertEquals("cheap", page.items().get(0).id());
    assertEquals("expensive", page.items().get(1).id());
  }

  @Test
  void list_sortPriceDesc_ordersByPriceDescending() {
    FakeStoreRepository repo =
        new FakeStoreRepository()
            .withItem(track("expensive", Genre.AFROBEATS, 50, "2026-01-01T00:00:00Z", 5000))
            .withItem(track("cheap", Genre.AFROBEATS, 50, "2026-01-01T00:00:00Z", 400));
    ListStoreService service = new ListStoreService(repo);

    Page<StoreItemView> page =
        service.list(
            new StoreQuery(Optional.empty(), Optional.empty(), StoreSort.PRICE_DESC), PageRequest.defaults());

    assertEquals("expensive", page.items().get(0).id());
    assertEquals("cheap", page.items().get(1).id());
  }

  @Test
  void list_pagination_slicesResults() {
    FakeStoreRepository repo = new FakeStoreRepository();
    for (int i = 0; i < 5; i++) {
      repo.withItem(track("t" + i, Genre.AFROBEATS, 50, "2026-01-01T00:00:00Z", 400));
    }
    ListStoreService service = new ListStoreService(repo);

    Page<StoreItemView> page = service.list(StoreQuery.NONE, new PageRequest(2, 2));

    assertEquals(5, page.total());
    assertEquals(2, page.items().size());
    assertEquals(2, page.page());
    assertEquals(2, page.size());
  }
}
