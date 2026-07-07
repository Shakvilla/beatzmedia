package org.shakvilla.beatzmedia.admin.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.admin.application.port.in.ListCuratedPlaylists;
import org.shakvilla.beatzmedia.admin.application.port.out.CuratedPlaylistRepository;
import org.shakvilla.beatzmedia.admin.domain.CuratedPlaylist;

/**
 * Application service for LLFR-ADMIN-06.1 (list curated playlists). Read-only; not audited. Admin
 * ADD §4.1.
 */
@ApplicationScoped
public class ListCuratedPlaylistsService implements ListCuratedPlaylists {

  private final CuratedPlaylistRepository curatedPlaylists;

  @Inject
  public ListCuratedPlaylistsService(CuratedPlaylistRepository curatedPlaylists) {
    this.curatedPlaylists = curatedPlaylists;
  }

  @Override
  public List<CuratedPlaylist> list() {
    return curatedPlaylists.list();
  }
}
