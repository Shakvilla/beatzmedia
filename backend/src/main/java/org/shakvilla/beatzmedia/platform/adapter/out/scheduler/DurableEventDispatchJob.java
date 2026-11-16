package org.shakvilla.beatzmedia.platform.adapter.out.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.platform.application.port.in.DurableEventHandler;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.DurableEventStore;
import org.shakvilla.beatzmedia.platform.domain.DurableEventEnvelope;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@ApplicationScoped
public class DurableEventDispatchJob implements ScheduledJob {

  static final String JOB_NAME = "platform.publication-outbox";
  static final int BATCH_SIZE = 25;
  static final int MAX_ATTEMPTS = 12;
  static final Duration CLAIM_LEASE = Duration.ofMinutes(2);

  private static final Logger LOG = Logger.getLogger(DurableEventDispatchJob.class);

  private final DurableEventStore store;
  private final Instance<DurableEventHandler> handlerBeans;
  private final Clock clock;
  private final DurableEventDispatchMetrics metrics;
  private final MeterRegistry meterRegistry;

  @Inject
  public DurableEventDispatchJob(
      DurableEventStore store,
      Instance<DurableEventHandler> handlerBeans,
      Clock clock,
      DurableEventDispatchMetrics metrics,
      MeterRegistry meterRegistry) {
    this.store = store;
    this.handlerBeans = handlerBeans;
    this.clock = clock;
    this.metrics = metrics;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public String jobName() {
    return JOB_NAME;
  }

  @Override
  public void runOnce() {
    Instant now = clock.now();
    List<DurableEventEnvelope> claimed = store.claim(BATCH_SIZE, now, CLAIM_LEASE);
    metrics.claimed(claimed.size());
    List<DurableEventHandler> handlers = new ArrayList<>();
    handlerBeans.forEach(handlers::add);

    for (DurableEventEnvelope event : claimed) {
      dispatchOne(event, handlers);
    }
    metrics.refresh(store.snapshot(clock.now()));
  }

  private void dispatchOne(
      DurableEventEnvelope event, List<DurableEventHandler> availableHandlers) {
    List<DurableEventHandler> matching = availableHandlers.stream()
        .filter(handler -> handler.eventType().equals(event.eventType()))
        .toList();
    if (matching.isEmpty()) {
      fail(event, new IllegalStateException("No handler registered for " + event.eventType()));
      return;
    }

    try {
      for (DurableEventHandler handler : matching) {
        if (store.wasHandled(event.id(), handler.consumerName())) {
          metrics.duplicateSkipped();
          continue;
        }
        Timer.Sample sample = metrics.start(meterRegistry);
        try {
          handler.handle(event);
          store.markHandled(event.id(), handler.consumerName(), clock.now());
        } finally {
          metrics.stop(sample);
        }
      }
      store.markCompleted(event.id(), clock.now());
      metrics.completed();
      LOG.infof(
          "publication outbox completed id=%s type=%s version=%d aggregate=%s/%s attempt=%d",
          event.id(),
          event.eventType(),
          event.schemaVersion(),
          event.aggregateType(),
          event.aggregateId(),
          event.attemptCount());
    } catch (Exception failure) {
      fail(event, failure);
    }
  }

  private void fail(DurableEventEnvelope event, Exception failure) {
    boolean terminal = event.attemptCount() >= MAX_ATTEMPTS;
    Duration delay = retryDelay(event.attemptCount());
    store.releaseForRetry(
        event.id(), clock.now().plus(delay), failure.getMessage(), terminal);
    metrics.retried(terminal);
    LOG.errorf(
        failure,
        "publication outbox failed id=%s type=%s version=%d aggregate=%s/%s attempt=%d terminal=%s",
        event.id(),
        event.eventType(),
        event.schemaVersion(),
        event.aggregateType(),
        event.aggregateId(),
        event.attemptCount(),
        terminal);
  }

  static Duration retryDelay(int attemptCount) {
    int exponent = Math.max(0, Math.min(attemptCount - 1, 8));
    return Duration.ofSeconds(Math.min(300L, 1L << exponent));
  }
}
