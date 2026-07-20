package org.shakvilla.beatzmedia.catalog.fakes;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

/**
 * Minimal hand-written fake for CDI {@link Event}, capturing everything {@link #fire} is called
 * with so unit tests can assert an in-module event was published (or not) without Mockito. Mirrors
 * {@code notifications.fakes.RecordingEvent} / {@code payments.fakes.RecordingEvent}. Only
 * synchronous {@code fire} is exercised by the catalog services under test.
 *
 * @param <T> the event type
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

  public int count() {
    return fired.size();
  }

  @Override
  public <U extends T> CompletionStage<U> fireAsync(U event) {
    throw new UnsupportedOperationException("fireAsync not supported in RecordingEvent");
  }

  @Override
  public <U extends T> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
    throw new UnsupportedOperationException("fireAsync not supported in RecordingEvent");
  }

  @Override
  public Event<T> select(Annotation... qualifiers) {
    throw new UnsupportedOperationException("select not supported in RecordingEvent");
  }

  @Override
  public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
    throw new UnsupportedOperationException("select not supported in RecordingEvent");
  }

  @Override
  public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
    throw new UnsupportedOperationException("select not supported in RecordingEvent");
  }
}
