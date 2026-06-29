package org.shakvilla.beatzmedia.library.fakes;

import java.util.HashSet;
import java.util.Set;

import org.shakvilla.beatzmedia.library.application.port.out.CatalogReader;

/** In-memory fake CatalogReader for unit tests. Seed with addTrack/addArtist/etc. */
public class FakeCatalogReader implements CatalogReader {

  private final Set<String> tracks = new HashSet<>();
  private final Set<String> artists = new HashSet<>();
  private final Set<String> albums = new HashSet<>();
  private final Set<String> shows = new HashSet<>();
  private final Set<String> playlists = new HashSet<>();

  public void addTrack(String id) { tracks.add(id); }
  public void addArtist(String id) { artists.add(id); }
  public void addAlbum(String id) { albums.add(id); }
  public void addShow(String id) { shows.add(id); }
  public void addPlaylist(String id) { playlists.add(id); }

  @Override public boolean trackExists(String trackId) { return tracks.contains(trackId); }
  @Override public boolean artistExists(String artistId) { return artists.contains(artistId); }
  @Override public boolean albumExists(String albumId) { return albums.contains(albumId); }
  @Override public boolean showExists(String showId) { return shows.contains(showId); }
  @Override public boolean playlistExists(String playlistId) { return playlists.contains(playlistId); }
}
