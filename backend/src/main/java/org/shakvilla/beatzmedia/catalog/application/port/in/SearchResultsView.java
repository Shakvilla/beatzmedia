package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;
import java.util.Optional;

/** Read-model for search results. LLFR-CATALOG-01.2, WU-CAT-2. */
public record SearchResultsView(
    List<TrackView> tracks,
    List<ArtistView> artists,
    List<AlbumView> albums,
    List<PlaylistView> playlists,
    Optional<TopResultView> topResult) {}
