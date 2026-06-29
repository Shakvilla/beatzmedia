package org.shakvilla.beatzmedia.library.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Output port: returns the list of track ids the account currently owns (granted by commerce on
 * settlement). Ownership is never stored in library (INV-LIB-5). The adapter delegates to the
 * commerce module's ownership port or a stub when commerce is not yet present.
 *
 * <p>Separate from {@code catalog.application.port.out.OwnershipReader} which handles per-track
 * decoration for the catalog module. Library ADD §4.2.
 */
public interface LibraryOwnershipReader {

  /** Returns all track ids the account currently owns, in no guaranteed order. */
  List<String> ownedTrackIds(AccountId account);
}
