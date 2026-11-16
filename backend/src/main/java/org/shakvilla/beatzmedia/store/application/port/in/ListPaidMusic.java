package org.shakvilla.beatzmedia.store.application.port.in;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.store.domain.StoreSort;

/** Lists purchasable music from the live catalog, separate from standalone Store products. */
public interface ListPaidMusic {
  Page<PaidMusicView> list(StoreSort sort, PageRequest page);
}
