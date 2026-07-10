package org.shakvilla.beatzmedia.audit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.application.service.ListAuditLogService;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditFilter;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditReader;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Unit tests for {@link ListAuditLogService}. Exercises filter, pagination, and ordering logic
 * against the in-memory {@link FakeAuditReader}. One test per LLFR-ADMIN-11.1 acceptance
 * criterion. Testing-strategy §2.
 */
@Tag("unit")
class ListAuditLogServiceTest {

  private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant T2 = Instant.parse("2026-06-01T00:00:00Z");
  private static final Instant T3 = Instant.parse("2026-06-27T10:00:00Z");

  private FakeAuditReader reader;
  private ListAuditLogService service;

  @BeforeEach
  void setUp() {
    reader = new FakeAuditReader();
    service = new ListAuditLogService(reader);

    reader.seed(
        entry("e1", "actor-1", "Alice", "Invited admin", "AdminMember", "m1",
            AuditType.SETTINGS, T1),
        entry("e2", "actor-2", "Bob", "Suspended user", "Account", "acc-2",
            AuditType.USER, T2),
        entry("e3", "actor-1", "Alice", "Approved catalog", "Album", "alb-3",
            AuditType.CATALOG, T3));
  }

  @Test
  void list_returns_all_entries_when_no_filter() {
    Page<AuditEntry> page = service.list(AuditFilter.none(), PageRequest.defaults());
    assertEquals(3, page.total());
    assertEquals(3, page.items().size());
  }

  @Test
  void list_orders_by_occurred_at_desc() {
    Page<AuditEntry> page = service.list(AuditFilter.none(), PageRequest.defaults());
    List<AuditEntry> items = page.items();
    // Newest first: T3, T2, T1
    assertEquals("e3", items.get(0).getId());
    assertEquals("e2", items.get(1).getId());
    assertEquals("e1", items.get(2).getId());
  }

  @Test
  void list_filters_by_type() {
    AuditFilter filter = new AuditFilter(AuditType.USER, null, null, null);
    Page<AuditEntry> page = service.list(filter, PageRequest.defaults());
    assertEquals(1, page.total());
    assertEquals("e2", page.items().get(0).getId());
  }

  @Test
  void list_filters_by_actor_display_name() {
    AuditFilter filter = new AuditFilter(null, "alice", null, null);
    Page<AuditEntry> page = service.list(filter, PageRequest.defaults());
    assertEquals(2, page.total());
  }

  @Test
  void list_filters_by_action_via_q() {
    AuditFilter filter = new AuditFilter(null, null, "Suspended", null);
    Page<AuditEntry> page = service.list(filter, PageRequest.defaults());
    assertEquals(1, page.total());
    assertEquals("e2", page.items().get(0).getId());
  }

  @Test
  void list_returns_empty_page_when_no_match() {
    AuditFilter filter = new AuditFilter(AuditType.FINANCE, null, null, null);
    Page<AuditEntry> page = service.list(filter, PageRequest.defaults());
    assertEquals(0, page.total());
    assertTrue(page.items().isEmpty());
  }

  @Test
  void list_paginates_correctly() {
    // page size 2, page 1
    Page<AuditEntry> page1 = service.list(AuditFilter.none(), new PageRequest(1, 2));
    assertEquals(3, page1.total());
    assertEquals(2, page1.items().size());

    // page 2
    Page<AuditEntry> page2 = service.list(AuditFilter.none(), new PageRequest(2, 2));
    assertEquals(3, page2.total());
    assertEquals(1, page2.items().size());
  }

  // ---- Helper ----

  private static AuditEntry entry(
      String id, String actorId, String actorName,
      String action, String targetType, String targetId,
      AuditType type, Instant at) {
    return new AuditEntry(id, actorId, actorName, action, targetType, targetId, type, null, at);
  }
}
