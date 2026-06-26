package org.shakvilla.beatzmedia.catalog.application.port.in;

/**
 * Read-model for one timed lyric line. Field name {@code time} matches the API contract shape
 * {@code { lines: { time, text }[] }} from {@code API-CONTRACT.md} §3 / LLFR-CATALOG-01.6.
 * {@code time} is seconds into the track.
 */
public record LyricLineView(int time, String text) {}
