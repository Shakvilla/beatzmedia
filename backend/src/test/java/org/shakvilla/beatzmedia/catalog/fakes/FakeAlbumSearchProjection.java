package org.shakvilla.beatzmedia.catalog.fakes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.application.port.out.AlbumSearchProjection;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;

/**
 * In-memory {@link AlbumSearchProjection}. Holds the albums currently carrying a search document, so
 * a test can assert what search would actually return rather than only that a method was called.
 */
public class FakeAlbumSearchProjection implements AlbumSearchProjection {

  private final Map<String, Album> documents = new LinkedHashMap<>();

  @Override
  public void index(Album album) {
    documents.put(album.getId().value(), album);
  }

  @Override
  public void remove(AlbumId id) {
    documents.remove(id.value());
  }

  /** The album document search would serve for this id, if any. */
  public Optional<Album> document(String albumId) {
    return Optional.ofNullable(documents.get(albumId));
  }

  /** How many album documents are currently indexed. */
  public int size() {
    return documents.size();
  }
}
