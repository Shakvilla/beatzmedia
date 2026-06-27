package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import org.shakvilla.beatzmedia.identity.application.port.in.AdminMemberView;

/**
 * Response DTO for admin-team endpoints. Matches {@code AdminMember} in
 * {@code Frontend/src/lib/admin-data.ts} and API-CONTRACT §14.
 *
 * <pre>
 * AdminMember { id, name, email, role, lastActive }
 * </pre>
 *
 * {@code role} is kebab-case (e.g. {@code "super-admin"}). {@code lastActive} is an ISO-8601
 * string or {@code null}. Identity ADD §6.
 */
public record AdminMemberDto(String id, String name, String email, String role, String lastActive) {

  public static AdminMemberDto from(AdminMemberView view) {
    return new AdminMemberDto(view.id(), view.name(), view.email(), view.role(), view.lastActive());
  }
}
