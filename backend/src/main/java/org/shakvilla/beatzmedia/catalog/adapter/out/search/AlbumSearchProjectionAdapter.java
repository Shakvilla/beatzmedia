package org.shakvilla.beatzmedia.catalog.adapter.out.search;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.application.port.out.AlbumSearchProjection;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.search.application.port.in.IndexEntityUseCase;
import org.shakvilla.beatzmedia.search.domain.EntityType;

/**
 * Drives an album's search document through the search module's input port.
 *
 * <p>Direction is catalog → search, matching {@link AlbumIndexSource} and
 * {@link ReleaseSearchProjectionObserver}: catalog owns the data and the mapping, search never reads
 * catalog. No new module edge.
 */
@ApplicationScoped
public class AlbumSearchProjectionAdapter implements AlbumSearchProjection {

  private final IndexEntityUseCase index;

  @Inject
  public AlbumSearchProjectionAdapter(IndexEntityUseCase index) {
    this.index = index;
  }

  @Override
  public void index(Album album) {
    index.index(CatalogIndexDocuments.fromAlbum(album));
  }

  @Override
  public void remove(AlbumId id) {
    index.deindex(EntityType.ALBUM, id.value());
  }
}
