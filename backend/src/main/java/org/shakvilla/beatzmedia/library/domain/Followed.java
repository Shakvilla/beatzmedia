package org.shakvilla.beatzmedia.library.domain;

import java.time.Instant;

/**
 * Domain event published (AFTER_SUCCESS) when an account follows a target (artist, playlist, or
 * show). Carries only ids — never a JPA entity. Consumed by {@code analytics} (WU-ANA-1) to roll up
 * {@code followersGained} for {@code kind=artist} follows; analytics never reads a library table
 * directly (hexagonal dependency rule) — this event is its sole source of follow facts. Other kinds
 * are ignored by analytics but are still published here since the event is a general library fact,
 * not analytics-specific.
 */
public record Followed(String accountId, FollowKind kind, String targetId, Instant at) {}
