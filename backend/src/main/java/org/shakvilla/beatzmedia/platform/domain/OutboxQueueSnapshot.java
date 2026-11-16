package org.shakvilla.beatzmedia.platform.domain;

/** Operational queue state refreshed by each dispatcher tick. */
public record OutboxQueueSnapshot(
    long pending, long processing, long failed, long oldestIncompleteAgeSeconds) {}
