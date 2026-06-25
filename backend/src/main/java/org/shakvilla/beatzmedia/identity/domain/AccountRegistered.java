package org.shakvilla.beatzmedia.identity.domain;

import java.time.Instant;

/**
 * Domain event published after a new fan account is successfully persisted. Consumers (catalog,
 * studio, notifications, analytics) observe this to initialise per-user state. Carries only the
 * minimal snapshot future consumers need — no JPA entities, no framework imports. Identity ADD §2 /
 * §5 (AFTER_SUCCESS pattern).
 *
 * <p>The event is fired synchronously by {@code RegisterFanService} after the repository save
 * returns, within or after the @Transactional boundary. Transactional-observer timing is the
 * consumer's responsibility.
 */
public record AccountRegistered(
    String accountId,
    String email,
    String name,
    Instant registeredAt) {}
