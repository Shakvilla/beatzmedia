package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.shakvilla.beatzmedia.admin.application.port.out.CuratedPlaylistRepository;
import org.shakvilla.beatzmedia.admin.domain.CuratedPlaylist;

/**
 * JPA implementation of {@link CuratedPlaylistRepository} (admin ADD §5.2 / §7, WU-ADM-4). Reads/
 * writes only this module's V951 {@code curated_playlist} table; no cross-module joins.
 * Transaction boundary = the calling application service.
 */
@ApplicationScoped
public class JpaCuratedPlaylistRepository implements CuratedPlaylistRepository {

  private final EntityManager em;

  @Inject
  public JpaCuratedPlaylistRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<CuratedPlaylist> list() {
    TypedQuery<CuratedPlaylistEntity> query = em.createQuery(
        "SELECT p FROM CuratedPlaylistEntity p ORDER BY p.name ASC", CuratedPlaylistEntity.class);
    return query.getResultList().stream().map(JpaCuratedPlaylistRepository::toDomain).toList();
  }

  @Override
  public CuratedPlaylist save(CuratedPlaylist playlist) {
    CuratedPlaylistEntity entity = new CuratedPlaylistEntity();
    entity.id = playlist.getId();
    entity.name = playlist.getName();
    em.persist(entity);
    return playlist;
  }

  private static CuratedPlaylist toDomain(CuratedPlaylistEntity entity) {
    return new CuratedPlaylist(entity.id, entity.name);
  }
}
