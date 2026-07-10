package org.shakvilla.beatzmedia.admin.application.port.in;

import java.time.Instant;
import java.util.Set;

/**
 * Response of {@code POST /admin/users/:id/impersonate}: {@code { token, expiresAt, scopes } }.
 * Admin ADD §6 (LLFR-ADMIN-02.5). Deliberately admin's own type (not a re-export of identity's
 * {@code identity.application.port.in.ImpersonationTokenView}) — admin's application layer never
 * depends on identity's port.in types directly, only on its own output port ({@code
 * AccountAdminPort}).
 */
public record ImpersonationTokenView(String token, Instant expiresAt, Set<String> scopes) {}
