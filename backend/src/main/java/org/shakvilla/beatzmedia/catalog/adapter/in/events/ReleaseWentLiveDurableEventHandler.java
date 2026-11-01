package org.shakvilla.beatzmedia.catalog.adapter.in.events;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.domain.ReleaseWentLive;
import org.shakvilla.beatzmedia.platform.application.port.in.DurableEventHandler;
import org.shakvilla.beatzmedia.platform.domain.DurableEventEnvelope;

@ApplicationScoped
public class ReleaseWentLiveDurableEventHandler implements DurableEventHandler {

  public static final String EVENT_TYPE = "catalog.release-went-live";
  public static final int SCHEMA_VERSION = 1;
  private static final String CONSUMER = "catalog.release-went-live-cdi.v1";

  private final Event<ReleaseWentLive> events;

  @Inject
  public ReleaseWentLiveDurableEventHandler(Event<ReleaseWentLive> events) {
    this.events = events;
  }

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  @Override
  public String consumerName() {
    return CONSUMER;
  }

  @Override
  public void handle(DurableEventEnvelope event) {
    if (event.schemaVersion() != SCHEMA_VERSION) {
      throw new IllegalArgumentException(
          "Unsupported " + EVENT_TYPE + " schema version " + event.schemaVersion());
    }
    events.fire(new ReleaseWentLive(
        required(event, "releaseId"),
        required(event, "artistId"),
        Instant.parse(required(event, "wentLiveAt"))));
  }

  private static String required(DurableEventEnvelope event, String field) {
    String value = event.payload().get(field);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(EVENT_TYPE + " payload missing " + field);
    }
    return value;
  }
}
