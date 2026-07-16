package org.shakvilla.beatzmedia.catalog.adapter.out.search;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.search.application.port.out.IndexSource;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;

/**
 * Supplies catalog playlists to the search module's reindex (WU-SRCH-2). Catalog owns the data and
 * the mapping; search never reads catalog. Same {@code module -> search} direction as
 * {@code store.adapter.out.persistence.SearchIndexPg}.
 */
@ApplicationScoped
public class PlaylistIndexSource implements IndexSource {

  private final CatalogRepository repository;

  @Inject
  public PlaylistIndexSource(CatalogRepository repository) {
    this.repository = repository;
  }

  @Override
  public EntityType entityType() {
    return EntityType.PLAYLIST;
  }

  @Override
  public List<IndexDocument> load() {
    return repository.allPlaylistsForIndex().stream().map(CatalogIndexDocuments::fromPlaylist).toList();
  }
}
