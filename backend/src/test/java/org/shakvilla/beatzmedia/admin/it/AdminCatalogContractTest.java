package org.shakvilla.beatzmedia.admin.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.CatalogItemDetailDto;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.CatalogItemDto;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.PagedCatalogDto;
import org.shakvilla.beatzmedia.admin.application.port.in.ActionLogEntryView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogCountsView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogItemDetailView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogItemRowView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogSplitView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogTrackView;
import org.shakvilla.beatzmedia.admin.application.port.in.PagedCatalogView;

/**
 * Contract test: verifies the WU-ADM-3 catalog-moderation response DTOs are structurally
 * compatible with {@code CatalogItem}/{@code CATALOG_COUNTS} in {@code
 * Frontend/src/lib/admin-data.ts} and admin ADD §6/§5.1 (LLFR-ADMIN-03.*). DoD §11 contract-test
 * requirement.
 *
 * <pre>
 * CatalogItem   { id, title, note?, artist, type, tracks, status }
 * PagedCatalog  { items, page, size, total, counts: { pending, published, takedown } }
 * CatalogItemDetail { id, title, note, artist, type, status, upc, tracklist, splits, actionLog }
 * </pre>
 */
@Tag("unit")
class AdminCatalogContractTest {

  private static final Instant NOW = Instant.parse("2026-07-11T10:00:00Z");

  @Test
  void catalog_item_dto_field_names_match_contract() {
    Set<String> names = new HashSet<>();
    for (var c : CatalogItemDto.class.getRecordComponents()) {
      names.add(c.getName());
    }
    assertTrue(names.contains("id"));
    assertTrue(names.contains("title"));
    assertTrue(names.contains("note"));
    assertTrue(names.contains("artist"));
    assertTrue(names.contains("type"));
    assertTrue(names.contains("tracks"));
    assertTrue(names.contains("status"));
    assertEquals(7, names.size(), "no extra fields beyond the contract shape");
  }

  @Test
  void paged_catalog_dto_has_items_page_size_total_counts() {
    PagedCatalogView view = new PagedCatalogView(
        List.of(new CatalogItemRowView("r1", "Iron Boy", null, "Black Sherif", "album", 14, "live")),
        1, 20, 1, new CatalogCountsView(0, 1, 0));

    PagedCatalogDto dto = PagedCatalogDto.from(view);

    assertEquals(1, dto.items().size());
    assertEquals(1, dto.page());
    assertEquals(20, dto.size());
    assertEquals(1, dto.total());
    assertEquals(1, dto.counts().published());
  }

  @Test
  void catalog_item_detail_dto_has_tracklist_splits_actionLog_and_honest_null_isrc_upc_note() {
    CatalogItemDetailView view = new CatalogItemDetailView(
        "r1", "Iron Boy", null, "Black Sherif", "album", "live", null,
        List.of(new CatalogTrackView(1, "t1", "Track One", null, 180, 500)),
        List.of(new CatalogSplitView("t1", "Black Sherif", "Primary artist", 70, "confirmed")),
        List.of(new ActionLogEntryView("a1", "APPROVE_RELEASE", "admin-1", NOW)));

    CatalogItemDetailDto dto = CatalogItemDetailDto.from(view);

    Set<String> names = new HashSet<>();
    for (var c : CatalogItemDetailDto.class.getRecordComponents()) {
      names.add(c.getName());
    }
    assertTrue(names.contains("tracklist"));
    assertTrue(names.contains("splits"));
    assertTrue(names.contains("actionLog"));
    assertTrue(names.contains("upc"));
    assertEquals(10, names.size());

    assertNull(dto.note(), "note is honest null (Category B)");
    assertNull(dto.upc(), "upc is honest null (Category B)");
    assertNull(dto.tracklist().get(0).isrc(), "isrc is honest null (Category B)");
    assertEquals(1, dto.splits().size());
    assertEquals(1, dto.actionLog().size());
    assertEquals("APPROVE_RELEASE", dto.actionLog().get(0).action());
  }
}
