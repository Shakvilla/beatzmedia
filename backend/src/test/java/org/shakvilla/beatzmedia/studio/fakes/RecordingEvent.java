package org.shakvilla.beatzmedia.studio.fakes;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

/**
 * Minimal hand-rolled fake for the CDI {@link Event} SPI (fakes-over-mocks convention), mirroring
 * {@code events.application.IssueTicketServiceTest.RecordingEvent}. Records every fired event for
 * assertion.
 */
public class RecordingEvent<T> implements Event<T> {

  private final List<T> fired = new ArrayList<>();

  @Override
  public void fire(T event) {
    fired.add(event);
  }

  public List<T> fired() {
    return List.copyOf(fired);
  }

  @Override
  public <U extends T> CompletionStage<U> fireAsync(U event) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <U extends T> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Event<T> select(Annotation... qualifiers) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
    throw new UnsupportedOperationException();
  }
}
