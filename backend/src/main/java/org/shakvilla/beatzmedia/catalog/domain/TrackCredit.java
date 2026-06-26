package org.shakvilla.beatzmedia.catalog.domain;

import java.util.List;

/**
 * Production / songwriting credit for a track. Value object. Catalog ADD §3 / {@code TrackCredit}
 * in {@code Frontend/src/types/index.ts}. Domain-layer; no framework imports.
 */
public record TrackCredit(String role, List<String> names) {}
