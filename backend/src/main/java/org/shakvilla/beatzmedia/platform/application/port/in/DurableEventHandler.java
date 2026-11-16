package org.shakvilla.beatzmedia.platform.application.port.in;

import org.shakvilla.beatzmedia.platform.domain.DurableEventEnvelope;

/** Named, version-aware at-least-once consumer of one durable event type. */
public interface DurableEventHandler {

  String eventType();

  String consumerName();

  int schemaVersion();

  void handle(DurableEventEnvelope event);
}
