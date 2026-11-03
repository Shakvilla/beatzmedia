package org.shakvilla.beatzmedia.engagement.adapter.in.rest;

public record CheckpointRequest(
    String contentKind,
    String contentId,
    String eventType,
    int positionSeconds,
    int durationSeconds,
    int listenedDeltaSeconds) {}
