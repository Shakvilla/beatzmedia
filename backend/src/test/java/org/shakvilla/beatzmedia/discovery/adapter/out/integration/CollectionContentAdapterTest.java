package org.shakvilla.beatzmedia.discovery.adapter.out.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetReleaseDiscoveryInfo;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.engagement.application.port.in.GetDiscoveryEngagementMetrics.ContentRef;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastEpisodeView;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastView;
import org.shakvilla.beatzmedia.studio.application.port.in.GetEpisodeDiscoveryInfo;

@Tag("unit")
class CollectionContentAdapterTest {

  @Test
  void groupsReleaseTracksAndShowEpisodesWithoutFlattening() {
    var adapter = new CollectionContentAdapter(
        (id, caller) -> track(id.value()),
        (id, includeTracks, caller) -> new AlbumView(
            id.value(), "EP", "artist-1", "Ama", 2026, "/ep", List.of("Afrobeats"),
            List.of("track-1", "track-2"), null, false),
        id -> new GetReleaseDiscoveryInfo.ReleaseDiscoveryInfo(
            id.value(), "artist-1", "EP", "Ama", "/ep", "ep",
            "2026-11-01T00:00:00Z", "GH"),
        new GetEpisodeDiscoveryInfo() {
          @Override
          public EpisodeDiscoveryInfo get(String artistId, String episodeId) {
            return episode(episodeId);
          }

          @Override
          public EpisodeDiscoveryInfo getPublished(String episodeId) {
            return episode(episodeId);
          }

          private EpisodeDiscoveryInfo episode(String episodeId) {
            return new EpisodeDiscoveryInfo(
                episodeId, "show-1", "Episode", "Show", "/show",
                "2026-11-02T00:00:00Z", "GH");
          }
        },
        id -> new PodcastView(
            id.value(), "Show", "Publisher", "/show", "Culture", "", 2, 10,
            null, true),
        (id, caller) -> List.of(
            podcastEpisode("episode-1"), podcastEpisode("episode-2")));

    var groups = adapter.resolve(List.of(
        new ContentRef("track", "track-1"),
        new ContentRef("track", "track-2"),
        new ContentRef("podcast-episode", "episode-1"),
        new ContentRef("podcast-episode", "episode-2")));

    assertEquals(2, groups.size());
    assertEquals("ep:ep-1", groups.getFirst().key());
    assertEquals("GH", groups.getFirst().originCountry());
    assertEquals(List.of("track-1", "track-2"), groups.getFirst().members().stream()
        .map(ContentRef::id).toList());
    assertEquals("podcast:show-1", groups.get(1).key());
    assertEquals("GH", groups.get(1).originCountry());
    assertEquals(List.of("episode-1", "episode-2"), groups.get(1).members().stream()
        .map(ContentRef::id).toList());
  }

  private static TrackView track(String id) {
    return new TrackView(
        id, "Track", "artist-1", "Ama", "ep-1", "EP", 180, "/track", "free",
        null, 10L, null, List.of(), "standard", 2026,
        Instant.parse("2026-11-01T00:00:00Z"), false);
  }

  private static PodcastEpisodeView podcastEpisode(String id) {
    return new PodcastEpisodeView(
        id, "show-1", "Episode", "Show", "/show", 600,
        "2026-11-02T00:00:00Z", "", 1, false, null, false, false, null);
  }
}
