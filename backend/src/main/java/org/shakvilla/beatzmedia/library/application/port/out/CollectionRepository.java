package org.shakvilla.beatzmedia.library.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.domain.FollowKind;
import org.shakvilla.beatzmedia.library.domain.LikeSets;
import org.shakvilla.beatzmedia.library.domain.PlaylistId;
import org.shakvilla.beatzmedia.library.domain.UserPlaylist;

/**
 * Output port for all library persistence: likes, follows, saves, and user playlists. Implemented
 * by {@code JpaCollectionRepository}. Library ADD §4.2.
 */
public interface CollectionRepository {

  /** Returns all id-list sets for the given account (newest-first). */
  LikeSets likeSets(AccountId account);

  /** Inserts a like row. Returns {@code true} if inserted, {@code false} if already existed. */
  boolean addLike(AccountId account, String trackId);

  /** Removes a like row. No-op if not present (idempotent). */
  void removeLike(AccountId account, String trackId);

  /**
   * Inserts a follow row for the given kind. Returns {@code true} if inserted.
   */
  boolean addFollow(AccountId account, FollowKind kind, String targetId);

  /** Removes a follow row. No-op if not present. */
  void removeFollow(AccountId account, FollowKind kind, String targetId);

  /** Inserts a saved-album row. Returns {@code true} if inserted. */
  boolean addSave(AccountId account, String albumId);

  /** Removes a saved-album row. No-op if not present. */
  void removeSave(AccountId account, String albumId);

  /** Returns all user playlists owned by the account, newest-first. */
  List<UserPlaylist> playlistsOf(AccountId account);

  /**
   * Returns the playlist owned by the account, or empty if not found / not owned. Never 403:
   * non-ownership is represented as absence (INV-LIB-2).
   */
  Optional<UserPlaylist> findPlaylist(AccountId account, PlaylistId playlist);

  /** Persists a new or mutated playlist (insert-or-update). */
  UserPlaylist save(UserPlaylist playlist);

  /**
   * Deletes a playlist owned by the account. No-op if not found or not owned.
   */
  void deletePlaylist(AccountId account, PlaylistId playlist);
}
