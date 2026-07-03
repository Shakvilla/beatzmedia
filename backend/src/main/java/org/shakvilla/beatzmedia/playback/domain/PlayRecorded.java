package org.shakvilla.beatzmedia.playback.domain;

import java.time.Instant;

/**
 * Domain event published (AFTER_SUCCESS) when a play is counted (not on a de-duped no-op). Carries
 * only ids + a minimal snapshot — consumed by {@code analytics} for plays/royalty rollups.
 * Playback ADD §9.
 */
public record PlayRecorded(
    String trackId, String accountId, Instant at, String fullVsPreview, String source) {}
