package org.shakvilla.beatzmedia.identity.application.port.in;

/**
 * Read-model of an account returned to callers of the auth/me use cases. Field names match
 * API-CONTRACT §2 {@code Account} and {@code Frontend/src/types/index.ts Account} exactly.
 * Identity ADD §6.
 */
public record AccountView(
    String id,
    String name,
    String email,
    String avatar,
    boolean isArtist,
    boolean isAdmin) {}
