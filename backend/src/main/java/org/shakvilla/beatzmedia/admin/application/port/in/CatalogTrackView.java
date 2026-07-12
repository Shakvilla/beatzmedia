package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * One row of {@code CatalogItemDetail.tracklist}. {@code position}/{@code trackId}/{@code title}/
 * {@code durationSec}/{@code priceMinor} are real (Category A — sourced from catalog's {@code
 * release_track}/{@code track} tables). {@code isrc} is always {@code null} (Category B — no ISRC
 * field exists anywhere in {@code catalog}; see admin ADD §13, WU-ADM-3 as-built). Admin ADD §6
 * (LLFR-ADMIN-03.1).
 */
public record CatalogTrackView(
    int position, String trackId, String title, String isrc, int durationSec, long priceMinor) {}
