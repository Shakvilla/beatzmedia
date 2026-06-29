package org.shakvilla.beatzmedia.library.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Input port: return the list of track ids the fan currently owns. LLFR-LIBRARY-01.6. */
public interface GetOwnedTrackIds {

  List<String> ownedTrackIds(AccountId account);
}
