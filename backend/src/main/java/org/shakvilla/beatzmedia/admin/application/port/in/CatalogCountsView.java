package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Whole-catalog moderation counts (Category A — a real, independent-of-filters aggregate,
 * matching {@code CATALOG_COUNTS} in {@code Frontend/src/lib/admin-data.ts}: {@code { pending,
 * published, takedown }}). Bucketed the same way as {@code CatalogFilter} (pending = {@code
 * draft+in_review}; published = {@code scheduled+live}; takedown = {@code takedown}). Admin ADD §6
 * (LLFR-ADMIN-03.1).
 */
public record CatalogCountsView(long pending, long published, long takedown) {}
