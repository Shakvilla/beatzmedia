package org.shakvilla.beatzmedia.studio.adapter.out.persistence;

import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeStatus;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ShowId;

/**
 * Maps {@code studio_podcast_show}/{@code studio_episode} JPA entities ↔ {@link PodcastShow}/{@link
 * Episode} domain aggregates. Domain carries no ORM/Jackson annotations (ArchUnit-enforced); this
 * is the only place the mapping happens. Studio ADD §5.2 / §7 (WU-STU-2).
 */
final class PodcastEntityMapper {

  private PodcastEntityMapper() {}

  static PodcastShow toDomain(PodcastShowEntity e) {
    return PodcastShow.reconstitute(
        new ShowId(e.id), new ArtistId(e.artistId), e.title, e.category, e.createdAt);
  }

  static PodcastShowEntity toEntity(PodcastShow show, PodcastShowEntity target) {
    PodcastShowEntity entity = target != null ? target : new PodcastShowEntity();
    entity.id = show.id().value();
    entity.artistId = show.artistId().value();
    entity.title = show.title();
    entity.category = show.category();
    entity.createdAt = show.createdAt();
    return entity;
  }

  static Episode toDomain(EpisodeEntity e) {
    return Episode.reconstitute(
        new EpisodeId(e.id),
        new ShowId(e.showId),
        new ArtistId(e.artistId),
        e.title,
        e.description,
        e.audioKey,
        e.coverUrl,
        e.durationSec,
        EpisodeStatus.valueOf(e.status),
        e.premium,
        e.priceMinor,
        Currency.valueOf(e.currency),
        e.earlyAccess,
        e.scheduledAt,
        e.publishedAt,
        e.plays,
        e.createdAt,
        e.idempotencyKey,
        e.requestHash);
  }

  static EpisodeEntity toEntity(Episode episode, EpisodeEntity target) {
    EpisodeEntity entity = target != null ? target : new EpisodeEntity();
    entity.id = episode.id().value();
    entity.showId = episode.showId().value();
    entity.artistId = episode.artistId().value();
    entity.title = episode.title();
    entity.description = episode.description();
    entity.audioKey = episode.audioKey();
    entity.coverUrl = episode.coverUrl();
    entity.durationSec = episode.durationSec();
    entity.status = episode.status().name();
    entity.premium = episode.premium();
    entity.priceMinor = episode.priceMinor();
    entity.currency = episode.currency() != null ? episode.currency().name() : Currency.GHS.name();
    entity.earlyAccess = episode.earlyAccess();
    entity.scheduledAt = episode.scheduledAt();
    entity.publishedAt = episode.publishedAt();
    entity.plays = episode.plays();
    entity.createdAt = episode.createdAt();
    entity.idempotencyKey = episode.idempotencyKey();
    entity.requestHash = episode.requestHash();
    return entity;
  }
}
