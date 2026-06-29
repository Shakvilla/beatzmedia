package org.shakvilla.beatzmedia.library.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.AlbumEntity;
import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.ArtistProfileEntity;
import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.PlaylistEntity;
import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.TrackEntity;
import org.shakvilla.beatzmedia.library.application.port.out.CatalogReader;

/**
 * Implements the library's {@link CatalogReader} port by querying the catalog JPA entities
 * in-process. No cross-module FK; uses the shared {@link EntityManager} targeting the same schema.
 * Library ADD §5.2.
 *
 * <p>Caller is the application-layer service; the EntityManager is injected and used within the
 * service's existing transaction boundary.
 */
@ApplicationScoped
public class CatalogReaderAdapter implements CatalogReader {

  private final EntityManager em;

  @Inject
  public CatalogReaderAdapter(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean trackExists(String trackId) {
    return em.find(TrackEntity.class, trackId) != null;
  }

  @Override
  public boolean artistExists(String artistId) {
    return em.find(ArtistProfileEntity.class, artistId) != null;
  }

  @Override
  public boolean albumExists(String albumId) {
    return em.find(AlbumEntity.class, albumId) != null;
  }

  @Override
  public boolean showExists(String showId) {
    // Shows are stored as ArtistShowEntity keyed by id
    Long count =
        em.createQuery(
                "SELECT COUNT(s) FROM ArtistShowEntity s WHERE s.id = :id", Long.class)
            .setParameter("id", showId)
            .getSingleResult();
    return count > 0;
  }

  @Override
  public boolean playlistExists(String playlistId) {
    return em.find(PlaylistEntity.class, playlistId) != null;
  }
}
