package org.shakvilla.beatzmedia.library.application.port.in;

import java.util.List;

/**
 * View of the fan's full collection as returned by GET /v1/me/collection. Library ADD §6 / API-CONTRACT §5.
 */
public record CollectionView(
    List<String> likedTracks,
    List<String> followedArtists,
    List<String> followedPlaylists,
    List<String> followedShows,
    List<String> savedAlbums,
    List<String> ownedTracks,
    List<UserPlaylistView> userPlaylists) {}
