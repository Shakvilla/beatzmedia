package org.shakvilla.beatzmedia.platform.application.port.in;

import org.shakvilla.beatzmedia.platform.domain.DurableEventFilter;
import org.shakvilla.beatzmedia.platform.domain.DurableEventRecord;
import org.shakvilla.beatzmedia.platform.domain.DurableQueueHealth;
import org.shakvilla.beatzmedia.platform.domain.Page;

public interface InspectDurableEvents {
  Page<DurableEventRecord> search(DurableEventFilter filter);

  DurableQueueHealth health();
}
