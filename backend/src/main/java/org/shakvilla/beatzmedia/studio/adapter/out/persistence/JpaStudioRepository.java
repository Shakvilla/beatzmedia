package org.shakvilla.beatzmedia.studio.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeStatus;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ShowId;
import org.shakvilla.beatzmedia.studio.domain.StudioProfile;
import org.shakvilla.beatzmedia.studio.domain.StudioSettings;

/**
 * JPA implementation of {@link StudioRepository}. Reads/writes {@code studio_profile}, {@code
 * studio_settings}, {@code studio_podcast_show} and {@code studio_episode}; no cross-module joins.
 * Transaction boundary = the application service. Studio ADD §5.2.
 */
@ApplicationScoped
public class JpaStudioRepository implements StudioRepository {

  private final EntityManager em;
  private final StudioProfileEntityMapper mapper;
  private final StudioSettingsEntityMapper settingsMapper;

  @Inject
  public JpaStudioRepository(
      EntityManager em, StudioProfileEntityMapper mapper, StudioSettingsEntityMapper settingsMapper) {
    this.em = em;
    this.mapper = mapper;
    this.settingsMapper = settingsMapper;
  }

  @Override
  public Optional<StudioProfile> findProfile(ArtistId artist) {
    StudioProfileEntity e = em.find(StudioProfileEntity.class, artist.value());
    return e == null ? Optional.empty() : Optional.of(mapper.toDomain(e));
  }

  @Override
  public boolean usernameTaken(String username, ArtistId excluding) {
    Long count = em.createQuery(
            "SELECT COUNT(p) FROM StudioProfileEntity p "
                + "WHERE lower(p.username) = lower(:username) AND p.artistId <> :excluding",
            Long.class)
        .setParameter("username", username)
        .setParameter("excluding", excluding.value())
        .getSingleResult();
    return count != null && count > 0;
  }

  @Override
  public StudioProfile saveProfile(StudioProfile profile) {
    StudioProfileEntity existing = em.find(StudioProfileEntity.class, profile.artistId().value());
    StudioProfileEntity entity = mapper.toEntity(profile, existing);
    if (existing == null) {
      em.persist(entity);
    } else {
      em.merge(entity);
    }
    return mapper.toDomain(entity);
  }

  // ---- Settings (WU-STU-4) ----

  @Override
  public Optional<StudioSettings> findSettings(ArtistId artist) {
    StudioSettingsEntity e = em.find(StudioSettingsEntity.class, artist.value());
    return e == null ? Optional.empty() : Optional.of(settingsMapper.toDomain(e));
  }

  @Override
  public StudioSettings saveSettings(StudioSettings settings) {
    StudioSettingsEntity existing = em.find(StudioSettingsEntity.class, settings.artistId().value());
    StudioSettingsEntity entity = settingsMapper.toEntity(settings, existing);
    if (existing == null) {
      em.persist(entity);
    } else {
      em.merge(entity);
    }
    return settingsMapper.toDomain(entity);
  }

  // ---- Podcast shows / episodes (WU-STU-2) ----

  @Override
  public List<PodcastShow> findShows(ArtistId artist) {
    return em.createQuery(
            "SELECT s FROM PodcastShowEntity s WHERE s.artistId = :artist ORDER BY s.createdAt",
            PodcastShowEntity.class)
        .setParameter("artist", artist.value())
        .getResultList()
        .stream()
        .map(PodcastEntityMapper::toDomain)
        .toList();
  }

  @Override
  public PodcastShow saveShow(PodcastShow show) {
    PodcastShowEntity existing = em.find(PodcastShowEntity.class, show.id().value());
    PodcastShowEntity entity = PodcastEntityMapper.toEntity(show, existing);
    if (existing == null) {
      em.persist(entity);
    } else {
      em.merge(entity);
    }
    return PodcastEntityMapper.toDomain(entity);
  }

  @Override
  public Optional<PodcastShow> findShow(ArtistId artist, ShowId id) {
    PodcastShowEntity e = em.find(PodcastShowEntity.class, id.value());
    if (e == null || !e.artistId.equals(artist.value())) {
      return Optional.empty();
    }
    return Optional.of(PodcastEntityMapper.toDomain(e));
  }

  @Override
  public List<Episode> findEpisodes(ArtistId artist) {
    return em.createQuery(
            "SELECT e FROM EpisodeEntity e WHERE e.artistId = :artist ORDER BY e.createdAt DESC",
            EpisodeEntity.class)
        .setParameter("artist", artist.value())
        .getResultList()
        .stream()
        .map(PodcastEntityMapper::toDomain)
        .toList();
  }

  @Override
  public Optional<Episode> findEpisode(ArtistId artist, EpisodeId id) {
    EpisodeEntity e = em.find(EpisodeEntity.class, id.value());
    if (e == null || !e.artistId.equals(artist.value())) {
      return Optional.empty();
    }
    return Optional.of(PodcastEntityMapper.toDomain(e));
  }

  @Override
  public Optional<Episode> findEpisodeByIdempotencyKey(ArtistId artist, String idempotencyKey) {
    return em.createQuery(
            "SELECT e FROM EpisodeEntity e WHERE e.artistId = :artist"
                + " AND e.idempotencyKey = :key",
            EpisodeEntity.class)
        .setParameter("artist", artist.value())
        .setParameter("key", idempotencyKey)
        .getResultStream()
        .findFirst()
        .map(PodcastEntityMapper::toDomain);
  }

  @Override
  public List<Episode> findDueScheduled(Instant now) {
    return em.createQuery(
            "SELECT e FROM EpisodeEntity e WHERE e.status = :status AND e.scheduledAt <= :now",
            EpisodeEntity.class)
        .setParameter("status", EpisodeStatus.scheduled.name())
        .setParameter("now", now)
        .getResultList()
        .stream()
        .map(PodcastEntityMapper::toDomain)
        .toList();
  }

  @Override
  public Episode saveEpisode(Episode episode) {
    EpisodeEntity existing = em.find(EpisodeEntity.class, episode.id().value());
    EpisodeEntity entity = PodcastEntityMapper.toEntity(episode, existing);
    if (existing == null) {
      em.persist(entity);
    } else {
      em.merge(entity);
    }
    return PodcastEntityMapper.toDomain(entity);
  }

  @Override
  public void deleteEpisode(EpisodeId id) {
    EpisodeEntity e = em.find(EpisodeEntity.class, id.value());
    if (e != null) {
      em.remove(e);
    }
  }
}
