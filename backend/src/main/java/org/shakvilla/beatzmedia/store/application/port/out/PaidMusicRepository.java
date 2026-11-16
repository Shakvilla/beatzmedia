package org.shakvilla.beatzmedia.store.application.port.out;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.store.application.port.in.PaidMusicView;
import org.shakvilla.beatzmedia.store.domain.StoreSort;

/** Read-only view of tracks and albums that the catalog currently offers for sale. */
public interface PaidMusicRepository {
  Page<PaidMusicView> find(StoreSort sort, PageRequest page);
}
