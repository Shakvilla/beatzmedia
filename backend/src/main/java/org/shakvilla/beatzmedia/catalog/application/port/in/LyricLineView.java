package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.math.BigDecimal;

/**
 * Read-model for one lyric line; null time marks plain text. Field name {@code time} matches the API contract shape
 * {@code { lines: { time, text }[] }} from {@code API-CONTRACT.md} §3 / LLFR-CATALOG-01.6.
 * When present, {@code time} is seconds into the track with millisecond precision.
 */
public record LyricLineView(BigDecimal time, String text) {}
