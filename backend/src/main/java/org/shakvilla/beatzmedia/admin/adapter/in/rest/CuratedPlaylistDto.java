package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import org.shakvilla.beatzmedia.admin.domain.CuratedPlaylist;

/**
 * Response DTO matching {@code CuratedPlaylist} in {@code Frontend/src/lib/admin-data.ts}: {@code
 * { id, name }}. Admin ADD §6 / LLFR-ADMIN-06.1.
 */
public record CuratedPlaylistDto(String id, String name) {

  public static CuratedPlaylistDto from(CuratedPlaylist playlist) {
    return new CuratedPlaylistDto(playlist.getId(), playlist.getName());
  }
}
