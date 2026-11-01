package org.shakvilla.beatzmedia.platform.application.port.out;

import org.shakvilla.beatzmedia.platform.domain.DurableEventMessage;

/** Appends an event using the caller's transaction. */
public interface DurableEventPublisher {

  void append(DurableEventMessage message);
}
