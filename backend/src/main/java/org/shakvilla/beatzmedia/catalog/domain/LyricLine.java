package org.shakvilla.beatzmedia.catalog.domain;

import java.math.BigDecimal;

/**
 * One lyric line. A null {@code tSec} represents untimed lyrics; otherwise time is seconds into the
 * track with millisecond precision. Catalog ADD §3 / LLFR-CATALOG-01.6. Domain-layer; no framework
 * imports.
 */
public record LyricLine(BigDecimal tSec, String text) {}
