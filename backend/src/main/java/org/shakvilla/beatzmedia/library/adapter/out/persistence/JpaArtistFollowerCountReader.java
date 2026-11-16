package org.shakvilla.beatzmedia.library.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.catalog.application.port.out.ArtistFollowerCountReader;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/** Counts the active relationships that the follow and unfollow endpoints actually mutate. */
@ApplicationScoped
public class JpaArtistFollowerCountReader implements ArtistFollowerCountReader {

  private final EntityManager em;

  @Inject
  public JpaArtistFollowerCountReader(EntityManager em) {
    this.em = em;
  }

  @Override
  public long count(ArtistId artistId) {
    return ((Number) em.createNativeQuery(
            "SELECT count(*) FROM followed_artist WHERE artist_id = :artistId")
        .setParameter("artistId", artistId.value())
        .getSingleResult()).longValue();
  }
}
