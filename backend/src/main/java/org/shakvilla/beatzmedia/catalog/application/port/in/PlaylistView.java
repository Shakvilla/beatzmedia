package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/**
 * Read-model / DTO for a playlist. Field names match the {@code Playlist} TypeScript type in
 * {@code Frontend/src/types/index.ts} and {@code API-CONTRACT.md} §3. The {@code tracks} list is
 * always populated (the detail endpoint embeds tracks). Catalog ADD §6 / LLFR-CATALOG-01.7.
 */
public record PlaylistView(
    String id,
    String title,
    String description,
    String creator,
    String creatorAvatar,
    String image,
    boolean isPublic,
    Long followers,
    List<String> trackIds,
    List<TrackView> tracks) {}
