package org.shakvilla.beatzmedia.catalog.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository.IndexableTrack;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.BrowseCategory;
import org.shakvilla.beatzmedia.catalog.domain.Lyrics;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.PlaylistId;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/** In-memory fake for unit tests. */
public class FakeCatalogRepository implements CatalogRepository {

  private final Map<String, ArtistProfile> artists = new HashMap<>();
  private final Map<String, Album> albums = new HashMap<>();
  private final Map<String, Track> tracks = new HashMap<>();
  private final Map<String, Lyrics> lyrics = new HashMap<>();
  private final Map<String, Playlist> playlists = new HashMap<>();
  private final List<BrowseCategory> browseCategories = new ArrayList<>();
  private final Map<String, Release> releases = new HashMap<>();
  private final Map<String, String> idempotencyKeys = new HashMap<>(); // key → releaseId
  private final Set<String> releasesWithPendingSplits = new HashSet<>();
  private final Map<String, Integer> markReadyCallCounts = new HashMap<>();
  private int trendingLimit = 10;

  /** Test helper: mark a release as having at least one pending SplitEntry (INV-12). */
  public void setHasPendingSplits(String releaseId, boolean pending) {
    if (pending) {
      releasesWithPendingSplits.add(releaseId);
    } else {
      releasesWithPendingSplits.remove(releaseId);
    }
  }

  /** Test helper: number of times markReleaseTracksReady was called for a release (idempotency). */
  public int markReadyCallCount(String releaseId) {
    return markReadyCallCounts.getOrDefault(releaseId, 0);
  }

  public void addRelease(Release release) {
    releases.put(release.getId(), release);
  }

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

  public void addBrowseCategory(BrowseCategory category) {
    browseCategories.add(category);
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

  @Override
  public List<BrowseCategory> browseCategories() {
    return List.copyOf(browseCategories);
  }

  @Override
  public List<Track> trendingTracks(int limit) {
    return tracks.values().stream()
        .sorted((a, b) -> Long.compare(
            b.getPlays().orElse(0L), a.getPlays().orElse(0L)))
        .limit(limit)
        .collect(Collectors.toList());
  }

  @Override
  public List<Track> top10Tracks(int limit) {
    return trendingTracks(limit);
  }

  @Override
  public List<Album> featuredAlbums(int limit) {
    return albums.values().stream().limit(limit).collect(Collectors.toList());
  }

  @Override
  public List<Album> albumsByIds(List<String> ids) {
    if (ids == null) return List.of();
    return ids.stream().map(albums::get).filter(a -> a != null).collect(Collectors.toList());
  }

  @Override
  public List<ArtistProfile> artistsByIds(List<String> ids) {
    if (ids == null) return List.of();
    return ids.stream().map(artists::get).filter(a -> a != null).collect(Collectors.toList());
  }

  @Override
  public List<Playlist> playlistsByIds(List<String> ids) {
    if (ids == null) return List.of();
    return ids.stream().map(playlists::get).filter(p -> p != null).collect(Collectors.toList());
  }

  // ---- WU-CAT-3 ----

  @Override
  public Page<Release> releasesByArtist(
      ArtistId owner, Optional<ReleaseStatus> status, PageRequest pageRequest) {
    List<Release> filtered = releases.values().stream()
        .filter(r -> r.getArtistId().equals(owner.value()))
        .filter(r -> status.isEmpty() || r.getStatus() == status.get())
        .toList();
    int from = pageRequest.page() * pageRequest.size();
    int to = Math.min(from + pageRequest.size(), filtered.size());
    List<Release> page = from >= filtered.size() ? List.of() : filtered.subList(from, to);
    return Page.of(page, pageRequest.page(), pageRequest.size(), filtered.size());
  }

  @Override
  public Optional<Release> findRelease(ReleaseId id) {
    return Optional.ofNullable(releases.get(id.value()));
  }

  @Override
  public void saveRelease(Release release) {
    releases.put(release.getId(), release);
  }

  @Override
  public void deleteRelease(ReleaseId id) {
    releases.remove(id.value());
  }

  @Override
  public void saveTrack(Track track) {
    tracks.put(track.getId().value(), track);
  }

  @Override
  public Optional<Release> findReleaseByIdempotencyKey(String idempotencyKey) {
    String releaseId = idempotencyKeys.get(idempotencyKey);
    if (releaseId == null) return Optional.empty();
    return Optional.ofNullable(releases.get(releaseId));
  }

  @Override
  public void saveReleaseWithIdempotencyKey(Release release, String idempotencyKey) {
    releases.put(release.getId(), release);
    if (idempotencyKey != null) {
      idempotencyKeys.put(idempotencyKey, release.getId());
    }
  }

  // ---- WU-CAT-4 ----

  @Override
  public boolean hasPendingSplits(ReleaseId releaseId) {
    return releasesWithPendingSplits.contains(releaseId.value());
  }

  @Override
  public List<Release> dueScheduled(Instant now) {
    return releases.values().stream()
        .filter(r -> r.getStatus() == ReleaseStatus.scheduled)
        .filter(r -> r.getScheduledAt() != null && !r.getScheduledAt().isAfter(now))
        .collect(Collectors.toList());
  }

  @Override
  public void markReleaseTracksReady(ReleaseId releaseId) {
    markReadyCallCounts.merge(releaseId.value(), 1, Integer::sum);
    Release release = releases.get(releaseId.value());
    if (release == null) {
      return;
    }
    for (var releaseTrack : release.getTracks()) {
      Track t = tracks.get(releaseTrack.trackId());
      if (t != null && !"ready".equals(t.getStatus())) {
        tracks.put(t.getId().value(), withStatus(t, "ready"));
      }
    }
  }

  // ---- WU-SRCH-2 ----

  @Override
  public List<IndexableTrack> allTracksForIndex() {
    // Domain Track carries no release-id field, so this fake cannot model the release-live gate;
    // it mirrors the "no release" arm of the real query (status = 'ready', always visible).
    return tracks.values().stream()
        .filter(t -> "ready".equals(t.getStatus()))
        .map(t -> new IndexableTrack(t, true))
        .collect(Collectors.toList());
  }

  @Override
  public List<ArtistProfile> allArtistsForIndex() {
    return new ArrayList<>(artists.values());
  }

  @Override
  public List<Album> allAlbumsForIndex() {
    return new ArrayList<>(albums.values());
  }

  @Override
  public List<Playlist> allPlaylistsForIndex() {
    return new ArrayList<>(playlists.values());
  }

  private static Track withStatus(Track t, String newStatus) {
    return new Track(
        t.getId(),
        t.getTitle(),
        t.getArtistId(),
        t.getArtistName(),
        t.getAlbumId().orElse(null),
        t.getAlbumTitle().orElse(null),
        t.getDurationSec(),
        t.getImage(),
        t.getOwnership(),
        t.getPriceMinor().orElse(null),
        t.getPlays().orElse(null),
        t.getAudioUrl().orElse(null),
        t.getCredits().orElse(null),
        t.getQuality().orElse(null),
        t.getYear().orElse(null),
        newStatus);
  }
}
