package org.shakvilla.beatzmedia.platform.adapter.out.scheduler;

import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.platform.domain.OutboxQueueSnapshot;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@ApplicationScoped
public class DurableEventDispatchMetrics {

  private final Counter claimed;
  private final Counter completed;
  private final Counter retried;
  private final Counter failed;
  private final Counter duplicateSkipped;
  private final Timer dispatchDuration;
  private final AtomicLong pendingGauge = new AtomicLong();
  private final AtomicLong processingGauge = new AtomicLong();
  private final AtomicLong failedGauge = new AtomicLong();
  private final AtomicLong oldestGauge = new AtomicLong();

  @Inject
  public DurableEventDispatchMetrics(MeterRegistry registry) {
    claimed = counter(registry, "publication.outbox.claimed", "Claimed durable events");
    completed = counter(registry, "publication.outbox.completed", "Completed durable events");
    retried = counter(registry, "publication.outbox.retried", "Durable events released for retry");
    failed = counter(registry, "publication.outbox.failed", "Terminal durable-event failures");
    duplicateSkipped = counter(
        registry, "publication.inbox.duplicate", "Handler calls skipped by an inbox receipt");
    dispatchDuration = Timer.builder("publication.outbox.dispatch.duration")
        .description("Wall-clock duration of one durable-event handler call")
        .register(registry);
    gauge(registry, "publication.outbox.pending", pendingGauge);
    gauge(registry, "publication.outbox.processing", processingGauge);
    gauge(registry, "publication.outbox.failed.current", failedGauge);
    gauge(registry, "publication.outbox.oldest.age.seconds", oldestGauge);
  }

  public void claimed(int count) {
    claimed.increment(count);
  }

  public void completed() {
    completed.increment();
  }

  public void retried(boolean terminal) {
    (terminal ? failed : retried).increment();
  }

  public void duplicateSkipped() {
    duplicateSkipped.increment();
  }

  public Timer.Sample start(MeterRegistry registry) {
    return Timer.start(registry);
  }

  public void stop(Timer.Sample sample) {
    sample.stop(dispatchDuration);
  }

  public void refresh(OutboxQueueSnapshot snapshot) {
    pendingGauge.set(snapshot.pending());
    processingGauge.set(snapshot.processing());
    failedGauge.set(snapshot.failed());
    oldestGauge.set(snapshot.oldestIncompleteAgeSeconds());
  }

  private static Counter counter(MeterRegistry registry, String name, String description) {
    return Counter.builder(name).description(description).register(registry);
  }

  private static void gauge(MeterRegistry registry, String name, AtomicLong value) {
    Gauge.builder(name, value, AtomicLong::get).register(registry);
  }
}
