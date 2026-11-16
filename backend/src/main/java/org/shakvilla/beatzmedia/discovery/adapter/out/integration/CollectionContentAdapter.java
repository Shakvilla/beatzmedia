package org.shakvilla.beatzmedia.discovery.adapter.out.integration;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetAlbum;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetReleaseDiscoveryInfo;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetTrack;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.discovery.application.port.out.CollectionContent;
import org.shakvilla.beatzmedia.engagement.application.port.in.GetDiscoveryEngagementMetrics.ContentRef;
import org.shakvilla.beatzmedia.podcasts.application.port.in.GetPodcast;
import org.shakvilla.beatzmedia.podcasts.application.port.in.ListEpisodes;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.studio.application.port.in.GetEpisodeDiscoveryInfo;

@ApplicationScoped
public class CollectionContentAdapter implements CollectionContent {

  private static final Logger LOG = Logger.getLogger(CollectionContentAdapter.class);

  private final GetTrack tracks;
  private final GetAlbum albums;
  private final GetReleaseDiscoveryInfo releases;
  private final GetEpisodeDiscoveryInfo episodes;
  private final GetPodcast shows;
  private final ListEpisodes showEpisodes;

  @Inject
  public CollectionContentAdapter(
      GetTrack tracks,
      GetAlbum albums,
      GetReleaseDiscoveryInfo releases,
      GetEpisodeDiscoveryInfo episodes,
      GetPodcast shows,
      ListEpisodes showEpisodes) {
    this.tracks = tracks;
    this.albums = albums;
    this.releases = releases;
    this.episodes = episodes;
    this.shows = shows;
    this.showEpisodes = showEpisodes;
  }

  @Override
  public List<Group> resolve(List<ContentRef> engagedContent) {
    Map<String, Group> groups = new LinkedHashMap<>();
    for (ContentRef ref : engagedContent) {
      try {
        Group group = switch (ref.kind()) {
          case "track" -> music(ref.id());
          case "podcast-episode" -> podcast(ref.id());
          default -> null;
        };
        if (group != null && !group.members().isEmpty()) groups.putIfAbsent(group.key(), group);
      } catch (RuntimeException unavailable) {
        // A candidate may have been unpublished between engagement aggregation and this read.
        LOG.debugf("Discovery content unavailable: %s:%s", ref.kind(), ref.id());
      }
    }
    return List.copyOf(groups.values());
  }

  private Group music(String trackId) {
    var track = tracks.get(new TrackId(trackId), Optional.empty());
    if (track.albumId() == null || track.albumId().isBlank()) return null;
    var release = releases.get(new ReleaseId(track.albumId()));
    var album = albums.get(new AlbumId(release.id()), false, Optional.empty());
    if (album.trackIds().isEmpty() || !album.trackIds().contains(trackId)) return null;
    String kind = switch (release.releaseType()) {
      case "single" -> "single";
      case "ep" -> "ep";
      case "album", "mixtape" -> "album";
      default -> null;
    };
    if (kind == null) return null;
    String id = "single".equals(kind) ? trackId : release.id();
    return new Group(
        kind, id, release.artistId(),
        "single".equals(kind) ? track.title() : release.title(),
        release.artistName(),
        "single".equals(kind) ? track.image() : album.coverImage(),
        "single".equals(kind) ? "/track/" + trackId : "/album/" + release.id(),
        Instant.parse(release.publishedAt()), album.genres(),
        album.trackIds().stream().map(member -> new ContentRef("track", member)).toList(),
        release.originCountry());
  }

  private Group podcast(String episodeId) {
    var episode = episodes.getPublished(episodeId);
    var show = shows.get(new PodcastId(episode.showId()));
    var members = showEpisodes.list(new PodcastId(show.id()), Optional.empty()).stream()
        .map(item -> new ContentRef("podcast-episode", item.id()))
        .toList();
    if (members.stream().noneMatch(member -> member.id().equals(episodeId))) return null;
    return new Group(
        "podcast", show.id(), show.id(), show.title(), show.publisher(), show.image(),
        "/podcast/" + show.id(), Instant.parse(episode.publishedAt()),
        show.category() == null ? List.of() : List.of(show.category()), members,
        episode.originCountry());
  }
}
