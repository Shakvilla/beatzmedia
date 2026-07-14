package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/**
 * Read-model for the batch catalog resolve endpoint. Every list contains only the ids that
 * actually resolved — unknown/removed ids and non-public playlists are silently omitted, never
 * an error. Catalog ADD §6.
 */
public record ResolvedCatalogView(
    List<TrackView> tracks,
    List<ArtistView> artists,
    List<AlbumView> albums,
    List<PlaylistView> playlists) {}
