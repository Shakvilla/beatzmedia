package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/**
 * Input port: artist profile and its sub-collections. LLFR-CATALOG-01.4. Catalog ADD §4.1.
 *
 * <p>Throws {@link org.shakvilla.beatzmedia.catalog.domain.ArtistNotFoundException} (→ 404
 * ARTIST_NOT_FOUND) for any unknown artist id.
 */
public interface GetArtist {

  ArtistView getArtist(ArtistId id);

  List<TrackView> tracks(ArtistId id, Optional<String> callerId);

  List<AlbumView> albums(ArtistId id);

  List<ShowView> shows(ArtistId id);
}
