package org.shakvilla.beatzmedia.library.application.port.out;

/**
 * Output port for catalog existence checks. Used to return 404 on unknown ids when the caller
 * likes/follows/saves or adds a track to a playlist. Library ADD §4.2.
 *
 * <p>Implemented by {@code CatalogReaderAdapter} which calls the catalog module's JPA repository
 * in-process (same JVM; no cross-module FK).
 */
public interface CatalogReader {

  boolean trackExists(String trackId);

  boolean artistExists(String artistId);

  boolean albumExists(String albumId);

  boolean showExists(String showId);

  boolean playlistExists(String playlistId);
}
