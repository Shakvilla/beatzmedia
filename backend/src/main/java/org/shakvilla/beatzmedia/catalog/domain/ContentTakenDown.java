package org.shakvilla.beatzmedia.catalog.domain;

import java.time.Instant;

/**
 * Domain event published after an admin takes a {@code live} release down. Consumers:
 * audit/notifications. Carries only ids + a minimal snapshot. Catalog ADD §2 / §9 /
 * LLFR-CATALOG-02.5.
 */
public record ContentTakenDown(
    String releaseId, String artistId, String takenDownBy, String reason, Instant occurredAt) {}
