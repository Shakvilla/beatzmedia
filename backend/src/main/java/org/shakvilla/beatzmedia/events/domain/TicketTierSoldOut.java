package org.shakvilla.beatzmedia.events.domain;

import java.time.Instant;

/**
 * Domain event published after-commit when a tier's issuance pushes it to {@code sold >=
 * capacity}. Consumers: notifications/analytics. Carries only ids + a minimal snapshot. Events ADD
 * §2 / §8 / §9.
 */
public record TicketTierSoldOut(String eventId, String tierId, Instant occurredAt) {}
