package org.shakvilla.beatzmedia.commerce.domain;

import java.time.Instant;
import java.util.List;

/**
 * Domain event published (AFTER_SUCCESS) when ownership grants are created for a settled order
 * (INV-1). Carries only ids + a minimal snapshot (never a JPA entity) — downstream consumers
 * (library owned-set, notifications "sale" alert) react to it. Emitted exactly once per settled
 * order (the exactly-once grant claim guarantees a single fan-out). Commerce ADD §5.2.
 */
public record OwnershipGranted(
    String orderId,
    String accountId,
    String reference,
    List<String> trackIds,
    List<String> episodeIds,
    Instant grantedAt) {}
