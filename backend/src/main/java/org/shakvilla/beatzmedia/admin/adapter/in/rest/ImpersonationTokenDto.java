package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.in.ImpersonationTokenView;

/**
 * Response DTO for {@code POST /admin/users/:id/impersonate}: {@code { token, expiresAt, scopes }
 * }. Admin ADD §6 (LLFR-ADMIN-02.5).
 */
public record ImpersonationTokenDto(String token, String expiresAt, List<String> scopes) {

  public static ImpersonationTokenDto from(ImpersonationTokenView view) {
    return new ImpersonationTokenDto(
        view.token(), view.expiresAt().toString(), List.copyOf(view.scopes()));
  }
}
