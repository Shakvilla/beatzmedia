package org.shakvilla.beatzmedia.library.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.domain.PlaylistId;

/**
 * Input port for all user-playlist CRUD operations. Library ADD §4.1 / LLFR-LIBRARY-01.5.
 * Consolidated into one interface to reduce interface proliferation while matching the ADD's
 * logical use cases.
 */
public interface ManageUserPlaylist {

  /** List all playlists owned by the account. */
  List<UserPlaylistView> listPlaylists(AccountId account);

  /** Create a new playlist. Returns the created playlist view (201). */
  UserPlaylistView createPlaylist(AccountId account, String title);

  /**
   * Get a single playlist by id. Owner-check: returns 404 ({@code PlaylistNotFoundException}) if
   * not owned or does not exist (INV-LIB-2).
   */
  UserPlaylistView getPlaylist(AccountId account, PlaylistId playlistId);

  /**
   * Rename a playlist. INV-LIB-3: title 1-100 chars. Owner-check: 404 if not owned.
   */
  UserPlaylistView renamePlaylist(AccountId account, PlaylistId playlistId, String title);

  /** Delete a playlist. Idempotent: 204 whether or not it existed and is owned. */
  void deletePlaylist(AccountId account, PlaylistId playlistId);

  /**
   * Add a track to a playlist (idempotent append). Validates track existence via CatalogReader.
   * Owner-check: 404 if not owned.
   */
  UserPlaylistView addTrack(AccountId account, PlaylistId playlistId, String trackId);

  /**
   * Remove a track from a playlist. Idempotent — no-op if track not in playlist. Owner-check: 404
   * if not owned.
   */
  UserPlaylistView removeTrack(AccountId account, PlaylistId playlistId, String trackId);
}
