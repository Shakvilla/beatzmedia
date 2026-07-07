package org.shakvilla.beatzmedia.admin.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.out.CuratedPlaylistRepository;
import org.shakvilla.beatzmedia.admin.domain.CuratedPlaylist;

/**
 * In-memory fake for {@link CuratedPlaylistRepository}. Testing-strategy §2.
 */
public class FakeCuratedPlaylistRepository implements CuratedPlaylistRepository {

  private final List<CuratedPlaylist> playlists = new ArrayList<>();

  @Override
  public List<CuratedPlaylist> list() {
    return playlists.stream().sorted(Comparator.comparing(CuratedPlaylist::getName)).toList();
  }

  @Override
  public CuratedPlaylist save(CuratedPlaylist playlist) {
    playlists.add(playlist);
    return playlist;
  }

  public int size() {
    return playlists.size();
  }
}
