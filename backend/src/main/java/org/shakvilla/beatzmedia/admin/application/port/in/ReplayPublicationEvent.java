package org.shakvilla.beatzmedia.admin.application.port.in;

import org.shakvilla.beatzmedia.platform.domain.DurableEventRecord;

public interface ReplayPublicationEvent {
  DurableEventRecord replay(String actorId, String eventId, String reason);
}
