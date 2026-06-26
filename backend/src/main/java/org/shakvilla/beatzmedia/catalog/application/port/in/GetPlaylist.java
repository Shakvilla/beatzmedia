package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.PlaylistId;

/**
 * Input port: playlist detail (+ embedded tracks). LLFR-CATALOG-01.7. Catalog ADD §4.1.
 *
 * <p>Private playlists accessed by a non-owner return 404 (existence hidden). Throws {@link
 * org.shakvilla.beatzmedia.catalog.domain.PlaylistNotFoundException} (→ 404) for unknown or
 * inaccessible playlists.
 */
public interface GetPlaylist {

  PlaylistView get(PlaylistId id, Optional<String> callerId);
}
