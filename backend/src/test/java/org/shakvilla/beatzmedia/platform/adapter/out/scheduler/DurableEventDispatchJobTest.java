package org.shakvilla.beatzmedia.platform.adapter.out.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.application.port.in.DurableEventHandler;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.DurableEventStore;
import org.shakvilla.beatzmedia.platform.domain.DurableEventEnvelope;
import org.shakvilla.beatzmedia.platform.domain.OutboxQueueSnapshot;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class DurableEventDispatchJobTest {

  private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

  @Test
  void isolatesPoisonEventAndCompletesTheRestOfTheBatch() {
    FakeStore store = new FakeStore(List.of(event("poison", 1), event("healthy", 1)));
    RecordingHandler handler = new RecordingHandler("publication.v1", Set.of("poison"));

    job(store, handler).runOnce();

    assertEquals(List.of("poison", "healthy"), handler.calls);
    assertEquals(List.of("healthy"), store.completed);
    assertEquals(List.of("poison"), store.retried);
    assertFalse(store.terminalFailures.contains("poison"));
  }

  @Test
  void skipsAlreadyReceiptedConsumerDuringReplay() {
    FakeStore store = new FakeStore(List.of(event("replayed", 2)));
    store.receipts.add("publication.v1/replayed");
    RecordingHandler handler = new RecordingHandler("publication.v1", Set.of());

    job(store, handler).runOnce();

    assertTrue(handler.calls.isEmpty());
    assertEquals(List.of("replayed"), store.completed);
    assertEquals(1.0, store.registry.get("publication.inbox.duplicate").counter().count());
  }

  @Test
  void marksExhaustedEventAsTerminalWithoutBlockingLaterEvents() {
    FakeStore store = new FakeStore(List.of(
        event("exhausted", DurableEventDispatchJob.MAX_ATTEMPTS), event("healthy", 1)));
    RecordingHandler handler = new RecordingHandler("publication.v1", Set.of("exhausted"));

    job(store, handler).runOnce();

    assertTrue(store.terminalFailures.contains("exhausted"));
    assertEquals(List.of("healthy"), store.completed);
  }

  @Test
  void retryDelayUsesBoundedExponentialBackoff() {
    assertEquals(Duration.ofSeconds(1), DurableEventDispatchJob.retryDelay(1));
    assertEquals(Duration.ofSeconds(8), DurableEventDispatchJob.retryDelay(4));
    assertEquals(Duration.ofSeconds(256), DurableEventDispatchJob.retryDelay(12));
    assertEquals(Duration.ofSeconds(256), DurableEventDispatchJob.retryDelay(100));
  }

  private static DurableEventDispatchJob job(FakeStore store, DurableEventHandler... handlers) {
    SimpleMeterRegistry registry = store.registry;
    return new DurableEventDispatchJob(
        store,
        new FakeInstance<>(List.of(handlers)),
        new FixedClock(),
        new DurableEventDispatchMetrics(registry),
        registry);
  }

  private static DurableEventEnvelope event(String id, int attempt) {
    return new DurableEventEnvelope(
        id, "catalog.release-went-live", 1, "release", id, java.util.Map.of("id", id), NOW,
        attempt);
  }

  private static final class RecordingHandler implements DurableEventHandler {
    private final String consumer;
    private final Set<String> failingIds;
    private final List<String> calls = new ArrayList<>();

    private RecordingHandler(String consumer, Set<String> failingIds) {
      this.consumer = consumer;
      this.failingIds = failingIds;
    }

    @Override public String eventType() { return "catalog.release-went-live"; }
    @Override public String consumerName() { return consumer; }

    @Override
    public void handle(DurableEventEnvelope event) {
      calls.add(event.id());
      if (failingIds.contains(event.id())) {
        throw new IllegalStateException("simulated poison event");
      }
    }
  }

  private static final class FakeStore implements DurableEventStore {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<DurableEventEnvelope> claimed;
    private final Set<String> receipts = new HashSet<>();
    private final List<String> completed = new ArrayList<>();
    private final List<String> retried = new ArrayList<>();
    private final Set<String> terminalFailures = new HashSet<>();

    private FakeStore(List<DurableEventEnvelope> claimed) {
      this.claimed = claimed;
    }

    @Override public List<DurableEventEnvelope> claim(int size, Instant now, Duration lease) {
      return claimed;
    }

    @Override public boolean wasHandled(String eventId, String consumerName) {
      return receipts.contains(consumerName + "/" + eventId);
    }

    @Override public void markHandled(String eventId, String consumerName, Instant processedAt) {
      receipts.add(consumerName + "/" + eventId);
    }

    @Override public void markCompleted(String eventId, Instant completedAt) {
      completed.add(eventId);
    }

    @Override
    public void releaseForRetry(
        String eventId, Instant availableAt, String error, boolean terminalFailure) {
      retried.add(eventId);
      if (terminalFailure) terminalFailures.add(eventId);
    }

    @Override public OutboxQueueSnapshot snapshot(Instant now) {
      return new OutboxQueueSnapshot(0, 0, terminalFailures.size(), 0);
    }
  }

  private static final class FixedClock implements Clock {
    @Override public Instant now() { return NOW; }
    @Override public LocalDate today(ZoneId zone) { return LocalDate.ofInstant(NOW, zone); }
  }

  @SuppressWarnings("all")
  private static final class FakeInstance<T> implements Instance<T> {
    private final List<T> values;
    private FakeInstance(List<T> values) { this.values = values; }
    @Override public Iterator<T> iterator() { return values.iterator(); }
    @Override public T get() { throw new UnsupportedOperationException(); }
    @Override public Instance<T> select(Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    @Override public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    @Override public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    @Override public boolean isUnsatisfied() { return values.isEmpty(); }
    @Override public boolean isAmbiguous() { return false; }
    @Override public void destroy(T instance) {}
    @Override public Handle<T> getHandle() { throw new UnsupportedOperationException(); }
    @Override public Iterable<? extends Handle<T>> handles() { throw new UnsupportedOperationException(); }
    @Override public boolean isResolvable() { return !values.isEmpty(); }
  }
}
