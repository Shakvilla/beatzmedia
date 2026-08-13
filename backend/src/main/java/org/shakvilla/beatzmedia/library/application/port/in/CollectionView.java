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
    /**
     * The subset of {@code ownedTracks} the account may currently download — sourced from each
     * track's ownership GRANT (captured once at settlement), never from the owning release's
     * current setting. An artist switching a release's downloads off after a sale does not remove
     * an already-owned track from this set: the buyer keeps the right they paid for, and this is
     * what lets the UI keep showing they have it. Task 9b.
     */
    List<String> downloadableTracks,
    List<UserPlaylistView> userPlaylists) {}
