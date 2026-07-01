package org.shakvilla.beatzmedia.catalog.domain;

import java.time.Instant;

/**
 * Domain event published after a release transitions to {@code live} — either via an immediate
 * admin approval or via the scheduled go-live job (INV-7). Consumers: analytics/notifications/
 * search. Carries only ids + a minimal snapshot. Catalog ADD §2 / §9 / LLFR-CATALOG-02.5.
 */
public record ReleaseWentLive(
    String releaseId, String artistId, Instant wentLiveAt) {}
