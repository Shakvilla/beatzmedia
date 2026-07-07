package org.shakvilla.beatzmedia.admin.application.port.in;

import org.shakvilla.beatzmedia.admin.domain.CuratedPlaylist;

/**
 * Input port: LLFR-ADMIN-06.1 — create a curated playlist reference. Auth: editor, super-admin.
 * Audited (INV-10, {@code type=editorial}). Admin ADD §4.1.
 */
public interface CreateCuratedPlaylist {

  /**
   * Creates a new curated playlist.
   *
   * @param actorId account id of the caller (JWT {@code sub}), used to stamp the audit entry
   * @param input the playlist fields
   * @return the persisted playlist
   */
  CuratedPlaylist create(String actorId, CuratedPlaylistInput input);

  /** Command DTO for {@code POST /admin/editorial/playlists}. */
  record CuratedPlaylistInput(String name) {}
}
