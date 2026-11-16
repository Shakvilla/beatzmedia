package org.shakvilla.beatzmedia.platform.domain;

public record DurableQueueHealth(
    String status,
    long pending,
    long processing,
    long failed,
    long completedLastHour,
    long oldestIncompleteAgeSeconds) {}
