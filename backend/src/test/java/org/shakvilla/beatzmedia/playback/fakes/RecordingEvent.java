package org.shakvilla.beatzmedia.playback.fakes;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

/**
 * Minimal hand-written fake for CDI {@link Event}, capturing every {@link #fire} so unit tests can
 * assert domain events published exactly once (no Mockito in the project). Mirrors commerce's /
 * payments' {@code RecordingEvent}.
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
    return this;
  }

  @Override
  public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
    throw new UnsupportedOperationException("select(subtype) not supported in RecordingEvent");
  }

  @Override
  public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
    throw new UnsupportedOperationException("select(subtype) not supported in RecordingEvent");
  }
}
