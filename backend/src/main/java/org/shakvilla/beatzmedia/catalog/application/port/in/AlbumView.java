package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/**
 * Read-model / DTO for an album. Field names match the {@code Album} TypeScript type in
 * {@code Frontend/src/types/index.ts} and {@code API-CONTRACT.md} §3. When {@code tracks} is
 * populated it reflects the {@code ?tracks=true} flag. Catalog ADD §6 / LLFR-CATALOG-01.5.
 */
public record AlbumView(
    String id,
    String title,
    String artistId,
    String artistName,
    int year,
    String coverImage,
    List<String> genres,
    List<String> trackIds,
    /** Embedded tracks; null when not requested (?tracks=true not set). */
    List<TrackView> tracks) {}
