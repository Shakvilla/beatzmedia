package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/** Read-model for the home feed. LLFR-CATALOG-01.1, WU-CAT-2 (+ WU-CAT-8 rails). */
public record HomeFeedView(
    List<TrackView> trending,
    List<TrackView> top10,
    List<AlbumView> featuredAlbums,
    RailsView rails) {

  /** WU-CAT-8 discover rails. */
  public record RailsView(
      List<AlbumView> newReleases,
      List<ArtistView> popularArtists,
      List<PlaylistView> curatedPlaylists) {}
}
