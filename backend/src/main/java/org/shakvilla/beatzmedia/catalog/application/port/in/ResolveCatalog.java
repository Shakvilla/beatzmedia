package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;
import java.util.Optional;

/**
 * Input port: batch-resolve id-lists across tracks/artists/albums/playlists in one call, so
 * frontend list screens (e.g. the library) fetch once instead of per-item. Catalog ADD §4.1.
 *
 * <p>Lenient: unknown/removed ids and non-public playlists are silently omitted from the result
 * (never a 404). Throws {@link org.shakvilla.beatzmedia.platform.domain.ValidationException}
 * (→ 422 VALIDATION) if any single list exceeds the per-kind id cap.
 */
public interface ResolveCatalog {

  ResolvedCatalogView resolve(Command command, Optional<String> callerId);

  /** Every field is nullable; a null list is treated the same as an empty one. */
  record Command(
      List<String> trackIds,
      List<String> artistIds,
      List<String> albumIds,
      List<String> playlistIds) {}
}
