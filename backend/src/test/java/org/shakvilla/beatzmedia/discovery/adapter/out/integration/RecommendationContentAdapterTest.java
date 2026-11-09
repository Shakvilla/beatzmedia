package org.shakvilla.beatzmedia.discovery.adapter.out.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ArtistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetArtist;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetReleaseDiscoveryInfo;
import org.shakvilla.beatzmedia.catalog.application.port.in.HomeFeedView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResolvedCatalogView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ShowView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrendingItemView;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.discovery.application.port.out.PersonalizationSignals;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastView;

@Tag("unit")
class RecommendationContentAdapterTest {

  @Test
  void emitsSinglesAlbumsEpsAndPodcastShowsWithoutEpisodeFanout() {
    AlbumView ep = album("ep-1", "EP One", List.of("ep-track"));
    AlbumView album = album("album-1", "Album One", List.of("album-track"));
    TrackView single = track("single-1", "single-release");
    TrackView albumTrack = track("album-track", "album-1");
    var home =
        new HomeFeedView(
            List.of(
                TrendingItemView.track(single),
                TrendingItemView.track(albumTrack),
                TrendingItemView.album("ep", ep, 50)),
            List.of(),
            List.of(album),
            new HomeFeedView.RailsView(List.of(), List.of(), List.of()));
    GetArtist artists = emptyArtists();
    GetReleaseDiscoveryInfo releaseInfo =
        id ->
            new GetReleaseDiscoveryInfo.ReleaseDiscoveryInfo(
                id.value(),
                "artist-1",
                "Release",
                "Ama",
                "/cover",
                id.value().startsWith("single")
                    ? "single"
                    : id.value().startsWith("ep") ? "ep" : "album",
                "2026-11-01T00:00:00Z");
    var show =
        new PodcastView(
            "show-1",
            "Accra After Dark",
            "233 Media",
            "/show",
            "Culture",
            "",
            12,
            80,
            null,
            true);
    var adapter =
        new RecommendationContentAdapter(
            caller -> home,
            artists,
            releaseInfo,
            (command, caller) ->
                new ResolvedCatalogView(List.of(), List.of(), List.of(), List.of()),
            (category, page) -> Page.of(List.of(show), 1, page.size(), 1),
            id -> show);

    var result = adapter.candidates("account-1", PersonalizationSignals.Snapshot.empty());

    assertEquals(
        Set.of("single", "album", "ep", "podcast"),
        result.stream()
            .map(candidate -> candidate.kind().wireValue())
            .collect(java.util.stream.Collectors.toSet()));
    assertEquals(
        1,
        result.stream().filter(candidate -> "single".equals(candidate.kind().wireValue())).count());
  }

  private static AlbumView album(String id, String title, List<String> tracks) {
    return new AlbumView(
        id,
        title,
        "artist-1",
        "Ama",
        2026,
        "/cover",
        List.of("Afrobeats"),
        tracks,
        null,
        false);
  }

  private static TrackView track(String id, String releaseId) {
    return new TrackView(
        id,
        "Single",
        "artist-1",
        "Ama",
        releaseId,
        "Single",
        180,
        "/cover",
        "free",
        null,
        100L,
        null,
        List.of(),
        "standard",
        2026,
        java.time.Instant.parse("2026-11-01T00:00:00Z"),
        false);
  }

  private static GetArtist emptyArtists() {
    return new GetArtist() {
      @Override
      public ArtistView getArtist(ArtistId id) {
        throw new UnsupportedOperationException();
      }

      @Override
      public List<TrackView> tracks(ArtistId id, Optional<String> callerId) {
        return List.of();
      }

      @Override
      public List<AlbumView> albums(ArtistId id) {
        return List.of();
      }

      @Override
      public List<ShowView> shows(ArtistId id) {
        return List.of();
      }
    };
  }
}
