package org.shakvilla.beatzmedia.admin.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.ModerationCaseDto;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.ModerationQueueDto;
import org.shakvilla.beatzmedia.admin.application.port.in.ModerationCaseView;
import org.shakvilla.beatzmedia.admin.application.port.in.ModerationQueueView;
import org.shakvilla.beatzmedia.admin.application.port.in.ModerationSummaryView;

/**
 * Contract test: verifies the WU-ADM-3 moderation-queue response DTOs are structurally compatible
 * with {@code ModerationItem}/{@code MOD_SLA_HOURS}/{@code MOD_ESCALATED} in {@code
 * Frontend/src/lib/admin-data.ts} and admin ADD §6/§5.1 (LLFR-ADMIN-04.1). DoD §11 contract-test
 * requirement.
 *
 * <pre>
 * ModerationCase { id, item, reporter, reason, time, severity, status, escalated }
 * ModerationQueue { items, page, size, total, summary: { openCount, slaHours, escalatedCount } }
 * </pre>
 */
@Tag("unit")
class AdminModerationContractTest {

  private static final Instant NOW = Instant.parse("2026-07-11T10:00:00Z");

  @Test
  void moderation_case_dto_field_names_match_contract() {
    Set<String> names = new HashSet<>();
    for (var c : ModerationCaseDto.class.getRecordComponents()) {
      names.add(c.getName());
    }
    assertTrue(names.contains("id"));
    assertTrue(names.contains("item"));
    assertTrue(names.contains("reporter"));
    assertTrue(names.contains("reason"));
    assertTrue(names.contains("time"));
    assertTrue(names.contains("severity"));
    assertTrue(names.contains("status"));
    assertTrue(names.contains("escalated"));
    assertEquals(8, names.size(), "no extra fields beyond the contract shape");
  }

  @Test
  void moderation_case_dto_time_is_iso8601() {
    ModerationCaseView view = new ModerationCaseView(
        "m1", "Release · \"X\" by Y", "admin-1", "Copyright", NOW, "high", "open", false);

    ModerationCaseDto dto = ModerationCaseDto.from(view);

    assertEquals(NOW.toString(), dto.time());
    assertTrue(dto.time().matches("^\\d{4}-\\d{2}-\\d{2}T.*Z$"), "time must be ISO-8601");
  }

  @Test
  void moderation_queue_dto_has_items_page_size_total_summary() {
    ModerationQueueView view = new ModerationQueueView(
        List.of(new ModerationCaseView("m1", "item", "r", "Spam", NOW, "low", "open", false)),
        1, 20, 1, new ModerationSummaryView(1, 6, 0));

    ModerationQueueDto dto = ModerationQueueDto.from(view);

    assertEquals(1, dto.items().size());
    assertEquals(1, dto.page());
    assertEquals(20, dto.size());
    assertEquals(1, dto.total());
    assertEquals(1, dto.summary().openCount());
    assertEquals(6, dto.summary().slaHours());
    assertEquals(0, dto.summary().escalatedCount());
  }
}
