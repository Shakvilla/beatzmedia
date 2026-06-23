package org.shakvilla.beatzmedia.media.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

import org.shakvilla.beatzmedia.media.domain.MediaReady;

/** Fake CDI Event for unit tests. Captures fired events for assertion. */
public class FakeMediaReadyEvent implements Event<MediaReady> {

  private final List<MediaReady> fired = new ArrayList<>();

  @Override
  public void fire(MediaReady event) {
    fired.add(event);
  }

  @Override
  public <U extends MediaReady> CompletionStage<U> fireAsync(U event) {
    fired.add(event);
    return java.util.concurrent.CompletableFuture.completedFuture(event);
  }

  @Override
  public <U extends MediaReady> CompletionStage<U> fireAsync(
      U event, NotificationOptions options) {
    return fireAsync(event);
  }

  @Override
  public Event<MediaReady> select(java.lang.annotation.Annotation... qualifiers) {
    return this;
  }

  @Override
  public <U extends MediaReady> Event<U> select(
      Class<U> subtype, java.lang.annotation.Annotation... qualifiers) {
    throw new UnsupportedOperationException("not needed in tests");
  }

  @Override
  public <U extends MediaReady> Event<U> select(
      TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) {
    throw new UnsupportedOperationException("not needed in tests");
  }

  public List<MediaReady> getFired() {
    return fired;
  }
}
