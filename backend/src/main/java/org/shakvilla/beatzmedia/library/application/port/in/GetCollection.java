package org.shakvilla.beatzmedia.library.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Input port: assemble the full collection for the authenticated fan. LLFR-LIBRARY-01.1. */
public interface GetCollection {

  CollectionView get(AccountId account);
}
