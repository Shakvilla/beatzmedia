package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.BrowseCategoryView;
import org.shakvilla.beatzmedia.catalog.application.service.ListBrowseCategoriesService;
import org.shakvilla.beatzmedia.catalog.domain.BrowseCategory;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;

/** Unit test for LLFR-CATALOG-01.3. Uses fake ports; no framework. */
@Tag("unit")
class ListBrowseCategoriesServiceTest {

  private FakeCatalogRepository repo;
  private ListBrowseCategoriesService service;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    service = new ListBrowseCategoriesService(repo);
  }

  @Test
  void list_returns_all_browse_categories() {
    repo.addBrowseCategory(new BrowseCategory("afrobeats", "Afrobeats", "from-orange-500 to-amber-400"));
    repo.addBrowseCategory(new BrowseCategory("hiplife", "Hiplife", "from-purple-500 to-pink-400"));

    List<BrowseCategoryView> views = service.list();

    assertEquals(2, views.size());
    assertEquals("afrobeats", views.get(0).id());
    assertEquals("Afrobeats", views.get(0).title());
    assertEquals("from-orange-500 to-amber-400", views.get(0).colorClass());
  }

  @Test
  void list_returns_empty_when_no_categories() {
    List<BrowseCategoryView> views = service.list();
    assertEquals(0, views.size());
  }
}
