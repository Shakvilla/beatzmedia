package org.shakvilla.beatzmedia.studio.fakes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeStatus;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ShowId;
import org.shakvilla.beatzmedia.studio.domain.StudioProfile;

/** In-memory fake for {@link StudioRepository} used in unit tests. */
public class FakeStudioRepository implements StudioRepository {

  private final Map<String, StudioProfile> profiles = new LinkedHashMap<>();
  private final Map<String, PodcastShow> shows = new LinkedHashMap<>();
  private final Map<String, Episode> episodes = new LinkedHashMap<>();

  public FakeStudioRepository withProfile(StudioProfile profile) {
    profiles.put(profile.artistId().value(), profile);
    return this;
  }

  public FakeStudioRepository withShow(PodcastShow show) {
    shows.put(show.id().value(), show);
    return this;
  }

  public FakeStudioRepository withEpisode(Episode episode) {
    episodes.put(episode.id().value(), episode);
    return this;
  }

  @Override
  public Optional<StudioProfile> findProfile(ArtistId artist) {
    return Optional.ofNullable(profiles.get(artist.value()));
  }

  @Override
  public boolean usernameTaken(String username, ArtistId excluding) {
    return profiles.values().stream()
        .anyMatch(
            p -> !p.artistId().equals(excluding)
                && p.username() != null
                && p.username().equalsIgnoreCase(username));
  }

  @Override
  public StudioProfile saveProfile(StudioProfile profile) {
    profiles.put(profile.artistId().value(), profile);
    return profile;
  }

  // ---- Podcast shows / episodes (WU-STU-2) ----

  @Override
  public List<PodcastShow> findShows(ArtistId artist) {
    return shows.values().stream().filter(s -> s.artistId().value().equals(artist.value())).toList();
  }

  @Override
  public PodcastShow saveShow(PodcastShow show) {
    shows.put(show.id().value(), show);
    return show;
  }

  @Override
  public Optional<PodcastShow> findShow(ArtistId artist, ShowId id) {
    PodcastShow show = shows.get(id.value());
    if (show == null || !show.artistId().value().equals(artist.value())) {
      return Optional.empty();
    }
    return Optional.of(show);
  }

  @Override
  public List<Episode> findEpisodes(ArtistId artist) {
    return episodes.values().stream()
        .filter(e -> e.artistId().value().equals(artist.value()))
        .toList();
  }

  @Override
  public Optional<Episode> findEpisode(ArtistId artist, EpisodeId id) {
    Episode e = episodes.get(id.value());
    if (e == null || !e.artistId().value().equals(artist.value())) {
      return Optional.empty();
    }
    return Optional.of(e);
  }

  @Override
  public Optional<Episode> findEpisodeByIdempotencyKey(ArtistId artist, String idempotencyKey) {
    return episodes.values().stream()
        .filter(e -> e.artistId().value().equals(artist.value()))
        .filter(e -> idempotencyKey.equals(e.idempotencyKey()))
        .findFirst();
  }

  @Override
  public List<Episode> findDueScheduled(Instant now) {
    return episodes.values().stream()
        .filter(e -> e.status() == EpisodeStatus.scheduled)
        .filter(e -> e.scheduledAt() != null && !e.scheduledAt().isAfter(now))
        .toList();
  }

  @Override
  public Episode saveEpisode(Episode episode) {
    episodes.put(episode.id().value(), episode);
    return episode;
  }

  @Override
  public void deleteEpisode(EpisodeId id) {
    episodes.remove(id.value());
  }
}
