package org.shakvilla.beatzmedia.playback.adapter.in.rest;

/**
 * Wire DTO for {@code POST /v1/tracks/:id/play}. {@code source} defaults to {@code "player"} when
 * absent. Matches {@code API-CONTRACT.md} §4.
 */
public record RecordPlayRequest(String source) {}
