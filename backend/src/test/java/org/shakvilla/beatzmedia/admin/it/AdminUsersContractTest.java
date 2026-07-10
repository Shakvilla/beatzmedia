package org.shakvilla.beatzmedia.admin.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.AdminUserRowDto;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.DataExportJobRefDto;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.ImpersonationTokenDto;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.PagedUsersDto;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.UserDetailDto;
import org.shakvilla.beatzmedia.admin.application.port.in.ActionLogEntryView;
import org.shakvilla.beatzmedia.admin.application.port.in.AdminUserRowView;
import org.shakvilla.beatzmedia.admin.application.port.in.DataExportJobRefView;
import org.shakvilla.beatzmedia.admin.application.port.in.ImpersonationTokenView;
import org.shakvilla.beatzmedia.admin.application.port.in.PagedUsersView;
import org.shakvilla.beatzmedia.admin.application.port.in.UserDetailView;
import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader.UserCounts;

/**
 * Contract test: verifies the WU-ADM-2 response DTOs are structurally compatible with {@code
 * AdminUserRow}/{@code UserDetail}/{@code ImpersonationToken} in {@code
 * Frontend/src/lib/admin-data.ts} and API-CONTRACT.md §12 (Users). Admin ADD §6 / DoD §11
 * contract-test requirement (LLFR-ADMIN-02.*).
 *
 * <pre>
 * AdminUserRow { id, name, initial, email, role: 'fan'|'artist', verified, joined, lastActive, status }
 * UserDetail   { summary, activity, orders, devices, actionLog } (API-CONTRACT wire shape)
 * ImpersonationToken { token, expiresAt, scopes }
 * DataExportJobRef   { jobId, status }
 * </pre>
 */
@Tag("unit")
class AdminUsersContractTest {

  private static final Instant NOW = Instant.parse("2026-07-10T10:00:00Z");

  @Test
  void admin_user_row_dto_field_names_match_contract() {
    Set<String> names = new HashSet<>();
    for (var c : AdminUserRowDto.class.getRecordComponents()) {
      names.add(c.getName());
    }
    assertTrue(names.contains("id"));
    assertTrue(names.contains("name"));
    assertTrue(names.contains("initial"));
    assertTrue(names.contains("email"));
    assertTrue(names.contains("role"));
    assertTrue(names.contains("verified"));
    assertTrue(names.contains("joined"));
    assertTrue(names.contains("lastActive"));
    assertTrue(names.contains("status"));
    assertEquals(9, names.size(), "no extra fields beyond the contract shape");
  }

  @Test
  void admin_user_row_dto_role_is_fan_or_artist_and_timestamps_are_iso8601() {
    AdminUserRowView fan = new AdminUserRowView(
        "u1", "Ama Boateng", "A", "ama@example.com", "fan", false, NOW, NOW, "active");
    AdminUserRowView artist = new AdminUserRowView(
        "u2", "Black Sherif", "B", "team@blacksherif.com", "artist", true, NOW, NOW, "active");

    AdminUserRowDto fanDto = AdminUserRowDto.from(fan);
    AdminUserRowDto artistDto = AdminUserRowDto.from(artist);

    assertEquals("fan", fanDto.role());
    assertEquals("artist", artistDto.role());
    assertEquals(NOW.toString(), fanDto.joined());
    assertEquals(NOW.toString(), fanDto.lastActive());
    assertTrue(fanDto.joined().matches("^\\d{4}-\\d{2}-\\d{2}T.*Z$"), "joined must be ISO-8601");
  }

  @Test
  void paged_users_dto_has_items_page_size_total_counts() {
    PagedUsersView view = new PagedUsersView(
        List.of(new AdminUserRowView("u1", "Ama", "A", "ama@x.com", "fan", false, NOW, NOW, "active")),
        1, 20, 1, new UserCounts(1, 1, 0, 0, 0));

    PagedUsersDto dto = PagedUsersDto.from(view);

    assertEquals(1, dto.items().size());
    assertEquals(1, dto.page());
    assertEquals(20, dto.size());
    assertEquals(1, dto.total());
    assertEquals(1, dto.counts().all());
  }

  @Test
  void user_detail_dto_has_summary_activity_orders_devices_actionLog() {
    UserDetailView view = new UserDetailView(
        new AdminUserRowView("u1", "Ama", "A", "ama@x.com", "fan", false, NOW, NOW, "active"),
        List.of(), List.of(), List.of(),
        List.of(new ActionLogEntryView("a1", "Verified artist", "admin-1", NOW)));

    UserDetailDto dto = UserDetailDto.from(view);

    Set<String> names = new HashSet<>();
    for (var c : UserDetailDto.class.getRecordComponents()) {
      names.add(c.getName());
    }
    assertTrue(names.contains("summary"));
    assertTrue(names.contains("activity"));
    assertTrue(names.contains("orders"));
    assertTrue(names.contains("devices"));
    assertTrue(names.contains("actionLog"));
    assertEquals(5, names.size());

    assertEquals("u1", dto.summary().id());
    assertEquals(0, dto.activity().size());
    assertEquals(0, dto.orders().size());
    assertEquals(0, dto.devices().size());
    assertEquals(1, dto.actionLog().size());
    assertEquals("Verified artist", dto.actionLog().get(0).action());
  }

  @Test
  void impersonation_token_dto_has_token_expiresAt_scopes() {
    ImpersonationTokenView view = new ImpersonationTokenView("jwt-value", NOW, Set.of("fan"));

    ImpersonationTokenDto dto = ImpersonationTokenDto.from(view);

    assertEquals("jwt-value", dto.token());
    assertEquals(NOW.toString(), dto.expiresAt());
    assertEquals(List.of("fan"), dto.scopes());
  }

  @Test
  void data_export_job_ref_dto_has_jobId_and_status_queued() {
    DataExportJobRefView view = new DataExportJobRefView("job-1", "queued");

    DataExportJobRefDto dto = DataExportJobRefDto.from(view);

    assertEquals("job-1", dto.jobId());
    assertEquals("queued", dto.status());
  }
}
