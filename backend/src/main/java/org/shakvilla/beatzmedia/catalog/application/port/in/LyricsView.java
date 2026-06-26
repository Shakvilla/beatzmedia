package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/**
 * Read-model for track lyrics. Matches the API contract shape {@code { lines: { time, text }[] }}
 * from {@code API-CONTRACT.md} §3 / LLFR-CATALOG-01.6.
 */
public record LyricsView(List<LyricLineView> lines) {}
