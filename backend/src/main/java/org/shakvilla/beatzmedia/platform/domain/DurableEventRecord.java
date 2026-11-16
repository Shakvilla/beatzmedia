package org.shakvilla.beatzmedia.platform.domain;

import java.time.Instant;

/** Operator-safe outbox metadata. The serialized event payload is deliberately absent. */
public record DurableEventRecord(
    String id,
    String eventType,
    int schemaVersion,
    String aggregateType,
    String aggregateId,
    Instant occurredAt,
    Instant availableAt,
    DurableEventStatus status,
    int attemptCount,
    int replayCount,
    Instant claimedUntil,
    Instant completedAt,
    String lastError,
    Instant lastReplayedAt,
    String lastReplayedBy) {}
