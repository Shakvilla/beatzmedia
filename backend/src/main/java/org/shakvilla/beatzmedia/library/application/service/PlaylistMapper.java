package org.shakvilla.beatzmedia.library.application.service;

import java.util.List;

import org.shakvilla.beatzmedia.library.application.port.in.UserPlaylistView;
import org.shakvilla.beatzmedia.library.domain.UserPlaylist;

/** Maps between domain {@link UserPlaylist} and {@link UserPlaylistView}. */
final class PlaylistMapper {

  private PlaylistMapper() {}

  static UserPlaylistView toView(UserPlaylist p) {
    List<String> trackIds =
        p.tracks().stream()
            .sorted(java.util.Comparator.comparingInt(
                org.shakvilla.beatzmedia.library.domain.PlaylistTrack::position))
            .map(org.shakvilla.beatzmedia.library.domain.PlaylistTrack::trackId)
            .toList();
    return new UserPlaylistView(
        p.id(),
        p.title(),
        p.description(),
        trackIds,
        p.createdAt().toString());
  }
}
