package org.shakvilla.beatzmedia.store.application.port.in;

import org.shakvilla.beatzmedia.store.domain.StoreItemId;

/**
 * Fetch a single store product's full detail (type-specific children included).
 * LLFR-STORE-01.2. Public (no auth). Unknown id throws {@code StoreItemNotFoundException} (404).
 * Store ADD §4.1.
 */
public interface GetStoreItem {

  StoreItemView get(StoreItemId id);
}
