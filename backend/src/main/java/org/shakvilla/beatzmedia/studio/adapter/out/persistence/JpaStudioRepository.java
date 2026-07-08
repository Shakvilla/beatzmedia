package org.shakvilla.beatzmedia.studio.adapter.out.persistence;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.StudioProfile;

/**
 * JPA implementation of {@link StudioRepository}. Reads/writes only {@code studio_profile}; no
 * cross-module joins. Transaction boundary = the application service. Studio ADD §5.2.
 */
@ApplicationScoped
public class JpaStudioRepository implements StudioRepository {

  private final EntityManager em;
  private final StudioProfileEntityMapper mapper;

  @Inject
  public JpaStudioRepository(EntityManager em, StudioProfileEntityMapper mapper) {
    this.em = em;
    this.mapper = mapper;
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
}
