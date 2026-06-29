package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/** Read-model for the home feed. LLFR-CATALOG-01.1, WU-CAT-2. */
public record HomeFeedView(
    List<TrackView> trending,
    List<TrackView> top10,
    List<AlbumView> featuredAlbums) {}
