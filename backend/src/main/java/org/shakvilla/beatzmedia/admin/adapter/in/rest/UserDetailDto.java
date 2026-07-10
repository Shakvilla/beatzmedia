package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.in.ActionLogEntryView;
import org.shakvilla.beatzmedia.admin.application.port.in.UserDetailView;

/**
 * Response DTO for {@code GET /admin/users/:id}: {@code { summary, activity, orders, devices,
 * actionLog } }. Admin ADD §6 (LLFR-ADMIN-02.1). {@code activity}/{@code orders}/{@code devices}
 * are always empty arrays (Category B — see {@link UserDetailView}'s javadoc); {@code summary}/
 * {@code actionLog} are real (Category A).
 */
public record UserDetailDto(
    AdminUserRowDto summary,
    List<Object> activity,
    List<Object> orders,
    List<Object> devices,
    List<ActionLogEntryDto> actionLog) {

  public static UserDetailDto from(UserDetailView view) {
    return new UserDetailDto(
        AdminUserRowDto.from(view.summary()),
        view.activity(),
        view.orders(),
        view.devices(),
        view.actionLog().stream().map(ActionLogEntryDto::from).toList());
  }

  /** {@code UserActionLog}: {@code { id, action, by, time } }. */
  public record ActionLogEntryDto(String id, String action, String by, String time) {
    static ActionLogEntryDto from(ActionLogEntryView view) {
      return new ActionLogEntryDto(view.id(), view.action(), view.by(), view.time().toString());
    }
  }
}
