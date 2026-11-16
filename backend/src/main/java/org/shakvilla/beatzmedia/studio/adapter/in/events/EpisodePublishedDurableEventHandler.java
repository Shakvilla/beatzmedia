package org.shakvilla.beatzmedia.studio.adapter.in.events;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.platform.application.port.in.DurableEventHandler;
import org.shakvilla.beatzmedia.platform.domain.DurableEventEnvelope;
import org.shakvilla.beatzmedia.studio.domain.EpisodePublished;

@ApplicationScoped
public class EpisodePublishedDurableEventHandler implements DurableEventHandler {

  public static final String EVENT_TYPE = "studio.episode-published";
  public static final int SCHEMA_VERSION = 1;
  private static final String CONSUMER = "studio.episode-published-cdi.v1";

  private final Event<EpisodePublished> events;

  @Inject
  public EpisodePublishedDurableEventHandler(Event<EpisodePublished> events) {
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
  public int schemaVersion() {
    return SCHEMA_VERSION;
  }

  @Override
  public void handle(DurableEventEnvelope event) {
    if (event.schemaVersion() != SCHEMA_VERSION) {
      throw new IllegalArgumentException(
          "Unsupported " + EVENT_TYPE + " schema version " + event.schemaVersion());
    }
    events.fire(new EpisodePublished(
        required(event, "episodeId"),
        required(event, "showId"),
        required(event, "artistId"),
        Instant.parse(required(event, "occurredAt"))));
  }

  private static String required(DurableEventEnvelope event, String field) {
    String value = event.payload().get(field);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(EVENT_TYPE + " payload missing " + field);
    }
    return value;
  }
}
