package org.shakvilla.beatzmedia.commerce.domain;

import java.time.Instant;

/**
 * Domain event published (AFTER_SUCCESS) once per creator, alongside the 70/30 sale-split posting,
 * when a settled order grants ownership (INV-1/INV-4). Carries only ids + a minimal money snapshot
 * (minor units, INV-11) — never a JPA entity.
 *
 * <p>Consumed by {@code analytics} (WU-ANA-1) to roll up settled sales attributed to the recipient
 * creator (artist); analytics never reads a commerce/payments table directly (hexagonal dependency
 * rule) — this event is its sole source of settled-sales facts. Emitted exactly once per
 * {@code (orderId, creatorAccountId)} pair: {@link GrantOwnershipService} accumulates each creator's
 * gross across all their lines in the order and posts/fires once per creator, guarded by the same
 * exactly-once order-grant claim as the sale-split posting.
 */
public record SaleRecorded(
    String orderId, String creatorAccountId, long grossMinor, String currency, Instant settledAt) {}
