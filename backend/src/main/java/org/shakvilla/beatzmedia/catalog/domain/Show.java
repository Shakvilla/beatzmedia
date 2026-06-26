package org.shakvilla.beatzmedia.catalog.domain;

/**
 * A concert / event shown on an artist page. Value object. Catalog ADD §3 / {@code Show} in
 * {@code Frontend/src/types/index.ts}. Domain-layer; no framework imports.
 */
public record Show(
    /** ISO date string (e.g. "2026-05-22"). */
    String date,
    String city,
    String venue) {}
