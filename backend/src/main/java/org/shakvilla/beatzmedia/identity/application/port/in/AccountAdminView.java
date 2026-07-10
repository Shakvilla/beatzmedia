package org.shakvilla.beatzmedia.identity.application.port.in;

import java.time.Instant;

/**
 * Read-model of an account returned to the admin-facing mutation ports ({@link SuspendAccount},
 * {@link ReactivateAccount}, {@link VerifyArtist}). Deliberately distinct from {@link AccountView}
 * (used by {@code /me}, login, and signup) so that adding admin-only fields here (e.g. {@code
 * verified}, {@code status}) never changes the wire contract of those unrelated, already-shipped
 * endpoints. Identity ADD §3 / LLFR-ADMIN-02.2/.3/.4.
 */
public record AccountAdminView(
    String id,
    String name,
    String email,
    boolean isArtist,
    boolean verified,
    String status,
    Instant createdAt,
    Instant updatedAt) {}
