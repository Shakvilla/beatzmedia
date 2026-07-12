package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * One row of {@code CatalogItemDetail.splits} ("rights & splits"). Real (Category A) — sourced
 * from catalog's {@code split_entry} table for every track on the release. Admin ADD §6
 * (LLFR-ADMIN-03.1).
 */
public record CatalogSplitView(
    String trackId, String name, String role, int percent, String confirmation) {}
