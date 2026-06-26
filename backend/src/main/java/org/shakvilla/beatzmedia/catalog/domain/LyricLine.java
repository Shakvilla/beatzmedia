package org.shakvilla.beatzmedia.catalog.domain;

/**
 * One timed lyric line. Value object. {@code time} is in whole seconds. Catalog ADD §3 /
 * LLFR-CATALOG-01.6. Domain-layer; no framework imports.
 */
public record LyricLine(int tSec, String text) {}
