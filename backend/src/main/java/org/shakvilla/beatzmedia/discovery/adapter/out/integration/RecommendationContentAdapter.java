package org.shakvilla.beatzmedia.discovery.adapter.out.integration;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetArtist;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetHomeFeed;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetReleaseDiscoveryInfo;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResolveCatalog;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.discovery.application.port.out.PersonalizationSignals;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationContent;
import org.shakvilla.beatzmedia.discovery.domain.RecommendationCandidate;
import org.shakvilla.beatzmedia.discovery.domain.RecommendationKind;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.podcasts.application.port.in.GetPodcast;
import org.shakvilla.beatzmedia.podcasts.application.port.in.ListPodcasts;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastView;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;

@ApplicationScoped
public class RecommendationContentAdapter implements RecommendationContent {

  private static final Logger LOG = Logger.getLogger(RecommendationContentAdapter.class);
  private static final int MAX_FOLLOWED_ARTISTS = 8;
  private static final int MAX_TRACKS_PER_ARTIST = 6;
  private static final int MAX_RELEASES_PER_ARTIST = 6;
  private static final int MAX_RELEASES = 24;
  private static final int MAX_SHOWS = 12;

  private final GetHomeFeed home;
  private final GetArtist artists;
  private final GetReleaseDiscoveryInfo releases;
  private final ResolveCatalog catalog;
  private final ListPodcasts podcasts;
  private final GetPodcast podcast;

  @Inject
  public RecommendationContentAdapter(
      GetHomeFeed home,
      GetArtist artists,
      GetReleaseDiscoveryInfo releases,
      ResolveCatalog catalog,
      ListPodcasts podcasts,
      GetPodcast podcast) {
    this.home = home;
    this.artists = artists;
    this.releases = releases;
    this.catalog = catalog;
    this.podcasts = podcasts;
    this.podcast = podcast;
  }

  @Override
  public List<RecommendationCandidate> candidates(
      String accountId, PersonalizationSignals.Snapshot signals) {
    List<RecommendationCandidate> result = new ArrayList<>();
    result.addAll(music(accountId, signals));
    result.addAll(podcastShows(signals));
    return List.copyOf(result);
  }

  private List<RecommendationCandidate> music(
      String accountId, PersonalizationSignals.Snapshot signals) {
    Map<String, TrackView> tracks = new LinkedHashMap<>();
    Map<String, ReleaseSeed> releaseSeeds = new LinkedHashMap<>();
    try {
      var feed = home.get(Optional.of(accountId));
      feed.top10().forEach(track -> tracks.putIfAbsent(track.id(), track));
      feed.trending().stream()
          .filter(item -> item.track() != null)
          .forEach(item -> tracks.putIfAbsent(item.track().id(), item.track()));
      feed.trending().stream()
          .filter(item -> item.album() != null)
          .forEach(item -> addRelease(releaseSeeds, item.album(), item.type(), item.plays()));
      feed.featuredAlbums().forEach(album -> addRelease(releaseSeeds, album, null, 0));
      feed.rails().newReleases().forEach(album -> addRelease(releaseSeeds, album, null, 0));
    } catch (RuntimeException failure) {
      LOG.debug("Home candidates unavailable for personalization", failure);
    }
    signals.followedArtistIds().stream().sorted().limit(MAX_FOLLOWED_ARTISTS).forEach(
        artistId -> {
          try {
            artists.tracks(new ArtistId(artistId), Optional.of(accountId)).stream()
                .limit(MAX_TRACKS_PER_ARTIST)
                .forEach(track -> tracks.putIfAbsent(track.id(), track));
            artists.albums(new ArtistId(artistId)).stream()
                .limit(MAX_RELEASES_PER_ARTIST)
                .forEach(album -> addRelease(releaseSeeds, album, null, 0));
          } catch (RuntimeException failure) {
            LOG.debugf("Followed artist candidates unavailable for %s", artistId);
          }
        });

    Set<String> releaseIds = new LinkedHashSet<>(releaseSeeds.keySet());
    tracks.values().stream()
        .map(TrackView::albumId)
        .filter(id -> id != null && !id.isBlank())
        .forEach(releaseIds::add);
    Map<String, GetReleaseDiscoveryInfo.ReleaseDiscoveryInfo> releaseMetadata =
        releaseMetadata(releaseIds);

    Map<String, Long> popularityByRelease = new LinkedHashMap<>();
    tracks.values().forEach(
        track -> {
          if (track.albumId() == null) return;
          popularityByRelease.merge(
              track.albumId(), track.plays() == null ? 0 : track.plays(), Math::max);
        });
    Map<String, List<String>> genresByArtist = new LinkedHashMap<>();
    try {
      var resolved =
          catalog.resolve(
              new ResolveCatalog.Command(
                  List.of(),
                  tracks.values().stream().map(TrackView::artistId).distinct().limit(100).toList(),
                  releaseIds.stream().limit(100).toList(),
                  List.of()),
              Optional.of(accountId));
      resolved.artists().forEach(artist -> genresByArtist.put(artist.id(), artist.genres()));
      resolved.albums().forEach(
          album ->
              addRelease(
                  releaseSeeds,
                  album,
                  Optional.ofNullable(releaseMetadata.get(album.id()))
                      .map(GetReleaseDiscoveryInfo.ReleaseDiscoveryInfo::releaseType)
                      .orElse(null),
                  popularityByRelease.getOrDefault(album.id(), 0L)));
    } catch (RuntimeException failure) {
      LOG.debug("Catalog release grouping metadata unavailable for personalization", failure);
    }

    List<RecommendationCandidate> result = new ArrayList<>();
    tracks.values().stream()
        .filter(track -> isSingle(track, releaseMetadata))
        .map(
            track ->
                new RecommendationCandidate(
                    RecommendationKind.single,
                    track.id(),
                    track.artistId(),
                    track.artistName(),
                    track.title(),
                    track.artistName(),
                    track.image(),
                    "/track/" + track.id(),
                    genresByArtist.getOrDefault(track.artistId(), List.of()),
                    List.of(track.id()),
                    track.plays() == null ? 0 : track.plays(),
                    track.releasedAt()))
        .forEach(result::add);
    releaseSeeds.values().stream()
        .limit(MAX_RELEASES)
        .map(seed -> releaseCandidate(seed, releaseMetadata))
        .flatMap(Optional::stream)
        .forEach(result::add);
    return List.copyOf(result);
  }

  private List<RecommendationCandidate> podcastShows(PersonalizationSignals.Snapshot signals) {
    Map<String, PodcastView> shows = new LinkedHashMap<>();
    signals.followedShowIds().stream().sorted().limit(MAX_SHOWS).forEach(
        showId -> {
          try {
            var show = podcast.get(new PodcastId(showId));
            shows.put(show.id(), show);
          } catch (RuntimeException failure) {
            LOG.debugf("Followed show candidate unavailable for %s", showId);
          }
        });
    try {
      podcasts.list(Optional.empty(), new PageRequest(1, MAX_SHOWS)).items()
          .forEach(show -> shows.putIfAbsent(show.id(), show));
    } catch (RuntimeException failure) {
      LOG.debug("Popular podcast candidates unavailable for personalization", failure);
    }

    return shows.values().stream()
        .limit(MAX_SHOWS)
        .map(
            show ->
                new RecommendationCandidate(
                    RecommendationKind.podcast,
                    show.id(),
                    show.id(),
                    show.title(),
                    show.title(),
                    show.publisher(),
                    show.image(),
                    "/podcast/" + show.id(),
                    show.category() == null ? List.of() : List.of(show.category()),
                    List.of(),
                    show.popularity() == null ? 0 : show.popularity(),
                    null))
        .toList();
  }

  private Optional<RecommendationCandidate> releaseCandidate(
      ReleaseSeed seed,
      Map<String, GetReleaseDiscoveryInfo.ReleaseDiscoveryInfo> releaseMetadata) {
    String type = seed.releaseType();
    Instant publishedAt = null;
    var release = releaseMetadata.get(seed.album().id());
    if (release != null) {
      if (type == null) type = release.releaseType();
      publishedAt = parseInstant(release.publishedAt());
    }
    RecommendationKind kind = releaseKind(type);
    if (kind == null) return Optional.empty();
    AlbumView album = seed.album();
    return Optional.of(
        new RecommendationCandidate(
            kind,
            album.id(),
            album.artistId(),
            album.artistName(),
            album.title(),
            album.artistName(),
            album.coverImage(),
            "/album/" + album.id(),
            album.genres(),
            album.trackIds(),
            seed.popularity(),
            publishedAt));
  }

  private static RecommendationKind releaseKind(String releaseType) {
    if ("ep".equals(releaseType)) return RecommendationKind.ep;
    if ("album".equals(releaseType) || "mixtape".equals(releaseType) || releaseType == null) {
      return RecommendationKind.album;
    }
    return null;
  }

  private Map<String, GetReleaseDiscoveryInfo.ReleaseDiscoveryInfo> releaseMetadata(
      Set<String> releaseIds) {
    Map<String, GetReleaseDiscoveryInfo.ReleaseDiscoveryInfo> result = new LinkedHashMap<>();
    releaseIds.stream().limit(100).forEach(
        releaseId -> {
          try {
            result.put(releaseId, releases.get(new ReleaseId(releaseId)));
          } catch (RuntimeException failure) {
            LOG.debugf("Release metadata unavailable for recommendation %s", releaseId);
          }
        });
    return result;
  }

  private static boolean isSingle(
      TrackView track,
      Map<String, GetReleaseDiscoveryInfo.ReleaseDiscoveryInfo> releaseMetadata) {
    if (track.albumId() == null || track.albumId().isBlank()) return true;
    var release = releaseMetadata.get(track.albumId());
    return release == null || "single".equals(release.releaseType());
  }

  private static void addRelease(
      Map<String, ReleaseSeed> releases, AlbumView album, String type, long popularity) {
    releases.merge(
        album.id(),
        new ReleaseSeed(album, type, popularity),
        (existing, incoming) ->
            new ReleaseSeed(
                existing.album(),
                existing.releaseType() == null ? incoming.releaseType() : existing.releaseType(),
                Math.max(existing.popularity(), incoming.popularity())));
  }

  private static Instant parseInstant(String value) {
    if (value == null) return null;
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private record ReleaseSeed(AlbumView album, String releaseType, long popularity) {}
}
