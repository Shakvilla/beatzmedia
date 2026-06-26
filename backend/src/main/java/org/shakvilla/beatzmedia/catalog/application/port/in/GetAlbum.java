package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.AlbumId;

/**
 * Input port: album detail. LLFR-CATALOG-01.5. Catalog ADD §4.1.
 *
 * <p>Throws {@link org.shakvilla.beatzmedia.catalog.domain.AlbumNotFoundException} (→ 404
 * ALBUM_NOT_FOUND) for unknown album ids.
 */
public interface GetAlbum {

  /**
   * @param id album identifier
   * @param includeTracks when true, embed resolved {@link TrackView}s in the response
   * @param callerId calling account id (empty = anonymous) for ownership decoration
   */
  AlbumView get(AlbumId id, boolean includeTracks, Optional<String> callerId);
}
