package org.shakvilla.beatzmedia.platform.application.port.in;

import org.shakvilla.beatzmedia.platform.domain.DurableEventRecord;

public interface ReplayDurableEvent {
  DurableEventRecord replay(String eventId, String actorId);
}
