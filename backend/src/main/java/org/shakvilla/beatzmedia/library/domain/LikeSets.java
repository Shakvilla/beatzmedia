package org.shakvilla.beatzmedia.library.domain;

import java.util.List;

/**
 * Read model: all id-lists that make up a fan's collection (excluding owned tracks and playlists,
 * which are added by the application layer). Library ADD §3 / LLFR-LIBRARY-01.1.
 */
public record LikeSets(
    List<String> likedTrackIds,
    List<String> followedArtistIds,
    List<String> followedPlaylistIds,
    List<String> followedShowIds,
    List<String> savedAlbumIds) {}
