package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import org.shakvilla.beatzmedia.admin.application.port.in.AdminUserRowView;

/**
 * Response DTO matching {@code AdminUserRow} in {@code Frontend/src/lib/admin-data.ts}: {@code {
 * id, name, initial, email, role, verified, joined, lastActive, status } }. {@code joined}/{@code
 * lastActive} are real ISO-8601 timestamps ({@code createdAt}/{@code updatedAt}) — the frontend
 * mock's relative-ish strings ("2m ago") are mock flavor text, not part of the wire contract (same
 * resolution as every other WU this session that serializes real timestamps as ISO-8601). Admin
 * ADD §6 (LLFR-ADMIN-02.*).
 */
public record AdminUserRowDto(
    String id,
    String name,
    String initial,
    String email,
    String role,
    boolean verified,
    String joined,
    String lastActive,
    String status) {

  public static AdminUserRowDto from(AdminUserRowView view) {
    return new AdminUserRowDto(
        view.id(),
        view.name(),
        view.initial(),
        view.email(),
        view.role(),
        view.verified(),
        view.joined().toString(),
        view.lastActive().toString(),
        view.status());
  }
}
