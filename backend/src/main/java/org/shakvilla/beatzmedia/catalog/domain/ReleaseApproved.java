package org.shakvilla.beatzmedia.catalog.domain;

import java.time.Instant;

/**
 * Domain event published after an admin approves an {@code in_review} release (either
 * immediately, moving it to {@code live}, or for a future date, moving it to {@code scheduled}).
 * Consumers: admin/notifications/analytics. Carries only ids + a minimal snapshot — no JPA
 * entities, no framework imports. Catalog ADD §2 / §9 (AFTER_SUCCESS pattern) / LLFR-CATALOG-02.5.
 */
public record ReleaseApproved(
    String releaseId,
    String artistId,
    ReleaseStatus resultingStatus, // scheduled | live
    Instant scheduledAt, // non-null only when resultingStatus == scheduled
    String approvedBy,
    Instant occurredAt) {}
