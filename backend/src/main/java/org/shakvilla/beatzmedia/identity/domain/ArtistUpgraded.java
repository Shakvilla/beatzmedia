package org.shakvilla.beatzmedia.identity.domain;

import java.time.Instant;

/**
 * Domain event published after a fan account is successfully upgraded to artist. Consumers (catalog,
 * studio, notifications, analytics) observe this to initialise per-artist state — in particular,
 * the catalog module reacts to create the empty {@code artist_profile} shell. Identity does NOT
 * write the {@code artist_profile} table directly; that coupling is intentionally event-driven
 * (ADD §2 / §10 AFTER_SUCCESS pattern, hexagonal rule: no cross-module table writes).
 *
 * <p>The event is fired synchronously by {@code UpgradeToArtistService} after the account flag is
 * persisted, within the {@code @Transactional} boundary.
 */
public record ArtistUpgraded(
    String accountId,
    String email,
    String name,
    Instant upgradedAt) {}
