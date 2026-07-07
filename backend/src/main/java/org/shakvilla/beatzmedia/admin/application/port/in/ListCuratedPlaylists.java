package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.admin.domain.CuratedPlaylist;

/**
 * Input port: LLFR-ADMIN-06.1 — list curated playlists. Auth: editor, super-admin (write);
 * support (read). Admin ADD §4.1.
 */
public interface ListCuratedPlaylists {

  /** Returns all curated playlists ordered by name. */
  List<CuratedPlaylist> list();
}
