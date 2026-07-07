package org.shakvilla.beatzmedia.admin.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.admin.domain.CuratedPlaylist;

/**
 * Output port: persistence for {@link CuratedPlaylist}. Implemented by a JPA adapter in this
 * module ({@code curated_playlist} table). Admin ADD §4.2 / §7.
 */
public interface CuratedPlaylistRepository {

  /** Returns all curated playlists ordered by name. */
  List<CuratedPlaylist> list();

  /** Persists a new curated playlist. */
  CuratedPlaylist save(CuratedPlaylist playlist);
}
