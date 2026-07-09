package org.shakvilla.beatzmedia.studio.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ShowId;
import org.shakvilla.beatzmedia.studio.domain.StudioProfile;

/**
 * Output port for {@code studio_profile} + {@code studio_podcast_show} + {@code studio_episode}
 * persistence (WU-STU-1 + WU-STU-2 scope). The full ADD §4.2 {@code StudioRepository} also declares
 * settings methods over a table that doesn't exist yet in this WU ({@code studio_settings}); those
 * are added when WU-STU-4 lands its own table, rather than stubbed here ahead of scope. Studio ADD
 * §4.2.
 */
public interface StudioRepository {

  Optional<StudioProfile> findProfile(ArtistId artist);

  /** {@code true} if {@code username} (case-insensitive) is held by an artist other than {@code excluding}. */
  boolean usernameTaken(String username, ArtistId excluding);

  StudioProfile saveProfile(StudioProfile profile);

  // ---- Podcast shows / episodes (WU-STU-2) ----

  List<PodcastShow> findShows(ArtistId artist);

  PodcastShow saveShow(PodcastShow show);

  Optional<PodcastShow> findShow(ArtistId artist, ShowId id);

  List<Episode> findEpisodes(ArtistId artist);

  Optional<Episode> findEpisode(ArtistId artist, EpisodeId id);

  /** Idempotency lookup — WU-STU-2 §9: a replay of the same {@code (artist, idempotencyKey)}
   * returns the previously-created episode with no second media upload. */
  Optional<Episode> findEpisodeByIdempotencyKey(ArtistId artist, String idempotencyKey);

  /** All {@code scheduled} episodes whose {@code scheduledAt <= now} — indexed on {@code (status,
   * scheduled_at)}. Consumed exclusively by the {@code EpisodeGoLiveJob} scheduler (INV-7). */
  List<Episode> findDueScheduled(Instant now);

  Episode saveEpisode(Episode episode);

  void deleteEpisode(EpisodeId id);
}
