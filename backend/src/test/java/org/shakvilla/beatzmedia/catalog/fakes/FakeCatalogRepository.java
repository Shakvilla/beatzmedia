package org.shakvilla.beatzmedia.catalog.fakes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.Lyrics;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.PlaylistId;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/** In-memory fake for unit tests. */
public class FakeCatalogRepository implements CatalogRepository {

  private final Map<String, ArtistProfile> artists = new HashMap<>();
  private final Map<String, Album> albums = new HashMap<>();
  private final Map<String, Track> tracks = new HashMap<>();
  private final Map<String, Lyrics> lyrics = new HashMap<>();
  private final Map<String, Playlist> playlists = new HashMap<>();

  public void addArtist(ArtistProfile artist) {
    artists.put(artist.getId().value(), artist);
  }

  public void addAlbum(Album album) {
    albums.put(album.getId().value(), album);
  }

  public void addTrack(Track track) {
    tracks.put(track.getId().value(), track);
  }

  public void addLyrics(Lyrics l) {
    lyrics.put(l.getTrackId().value(), l);
  }

  public void addPlaylist(Playlist playlist) {
    playlists.put(playlist.getId().value(), playlist);
  }

  @Override
  public Optional<ArtistProfile> findArtist(ArtistId id) {
    return Optional.ofNullable(artists.get(id.value()));
  }

  @Override
  public List<Track> tracksByArtist(ArtistId id) {
    return tracks.values().stream()
        .filter(t -> t.getArtistId().value().equals(id.value()))
        .collect(Collectors.toList());
  }

  @Override
  public List<Album> albumsByArtist(ArtistId id) {
    return albums.values().stream()
        .filter(a -> a.getArtistId().value().equals(id.value()))
        .collect(Collectors.toList());
  }

  @Override
  public Optional<Album> findAlbum(AlbumId id) {
    return Optional.ofNullable(albums.get(id.value()));
  }

  @Override
  public List<Track> tracksByAlbum(AlbumId id) {
    return tracks.values().stream()
        .filter(t -> t.getAlbumId().map(a -> a.value().equals(id.value())).orElse(false))
        .collect(Collectors.toList());
  }

  @Override
  public Optional<Track> findTrack(TrackId id) {
    return Optional.ofNullable(tracks.get(id.value()));
  }

  @Override
  public Optional<Lyrics> findLyrics(TrackId id) {
    return Optional.ofNullable(lyrics.get(id.value()));
  }

  @Override
  public Optional<Playlist> findPlaylist(PlaylistId id) {
    return Optional.ofNullable(playlists.get(id.value()));
  }

  @Override
  public List<Track> tracksByIds(List<String> ids) {
    List<Track> result = new ArrayList<>();
    for (String id : ids) {
      Track t = tracks.get(id);
      if (t != null) {
        result.add(t);
      }
    }
    return result;
  }
}
