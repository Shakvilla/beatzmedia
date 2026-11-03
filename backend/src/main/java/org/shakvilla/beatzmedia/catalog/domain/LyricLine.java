package org.shakvilla.beatzmedia.catalog.domain;

/**
 * One lyric line. A null {@code tSec} represents untimed lyrics; otherwise time is in whole seconds. Catalog ADD §3 /
 * LLFR-CATALOG-01.6. Domain-layer; no framework imports.
 */
public record LyricLine(Integer tSec, String text) {}
