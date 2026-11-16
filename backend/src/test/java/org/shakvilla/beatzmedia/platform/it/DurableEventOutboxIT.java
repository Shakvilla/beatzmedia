package org.shakvilla.beatzmedia.platform.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.application.port.in.ReplayDurableEvent;
import org.shakvilla.beatzmedia.platform.application.port.out.DurableEventPublisher;
import org.shakvilla.beatzmedia.platform.application.port.out.DurableEventStore;
import org.shakvilla.beatzmedia.platform.domain.DurableEventEnvelope;
import org.shakvilla.beatzmedia.platform.domain.DurableEventMessage;

import io.agroal.api.AgroalDataSource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Tag("integration")
class DurableEventOutboxIT {

  private static final Instant BASE = Instant.parse("2026-11-01T12:00:00Z");

  @Inject DurableEventPublisher publisher;
  @Inject DurableEventStore store;
  @Inject ReplayDurableEvent replay;
  @Inject AgroalDataSource dataSource;

  @Test
  void appendParticipatesInTheAggregateTransaction() {
    String id = unique("rollback");

    assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
      publisher.append(message(id, BASE));
      throw new IllegalStateException("force aggregate rollback");
    }));

    assertEquals(0, count(id), "rolled-back aggregate transaction must not leak an outbox row");
  }

  @Test
  void expiredLeaseIsRecoveredAndInboxReceiptIsIdempotent() {
    String id = unique("lease");
    publisher.append(message(id, BASE));

    List<DurableEventEnvelope> first = store.claim(1, BASE, Duration.ofSeconds(10));
    assertEquals(id, first.getFirst().id());
    assertTrue(store.claim(1, BASE.plusSeconds(5), Duration.ofSeconds(10)).isEmpty());

    List<DurableEventEnvelope> recovered =
        store.claim(1, BASE.plusSeconds(11), Duration.ofSeconds(10));
    assertEquals(id, recovered.getFirst().id());
    assertEquals(2, recovered.getFirst().attemptCount());

    store.markHandled(id, "catalog.release.v1", BASE.plusSeconds(12));
    store.markHandled(id, "catalog.release.v1", BASE.plusSeconds(13));
    assertTrue(store.wasHandled(id, "catalog.release.v1"));
    assertEquals(1, receiptCount(id, "catalog.release.v1"));

    store.markCompleted(id, BASE.plusSeconds(14));
    assertEquals("completed", status(id));
  }

  @Test
  void concurrentWorkersClaimDisjointBatches() throws Exception {
    Set<String> expected = new HashSet<>();
    for (int i = 0; i < 8; i++) {
      String id = unique("concurrent-" + i);
      expected.add(id);
      publisher.append(message(id, BASE.plusMillis(i)));
    }

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    Future<List<DurableEventEnvelope>> first = pool.submit(() -> {
      barrier.await(5, TimeUnit.SECONDS);
      return store.claim(4, BASE.plusSeconds(1), Duration.ofMinutes(1));
    });
    Future<List<DurableEventEnvelope>> second = pool.submit(() -> {
      barrier.await(5, TimeUnit.SECONDS);
      return store.claim(4, BASE.plusSeconds(1), Duration.ofMinutes(1));
    });

    Set<String> firstIds = ids(first.get(20, TimeUnit.SECONDS));
    Set<String> secondIds = ids(second.get(20, TimeUnit.SECONDS));
    pool.shutdownNow();

    assertEquals(4, firstIds.size());
    assertEquals(4, secondIds.size());
    assertFalse(firstIds.stream().anyMatch(secondIds::contains), "workers must not double-claim");
    firstIds.addAll(secondIds);
    assertEquals(expected, firstIds);
  }

  @Test
  void concurrentReplayAllowsExactlyOneOperatorToRequeueTheEvent() throws Exception {
    String id = unique("concurrent-replay");
    publisher.append(message(id, BASE));
    store.releaseForRetry(id, BASE, "terminal", true);

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    java.util.concurrent.Callable<Boolean> operation = () -> {
      barrier.await(5, TimeUnit.SECONDS);
      try {
        replay.replay(id, "operator-" + Thread.currentThread().threadId());
        return true;
      } catch (org.shakvilla.beatzmedia.platform.domain.ConflictException lostRace) {
        return false;
      }
    };
    Future<Boolean> first = pool.submit(operation);
    Future<Boolean> second = pool.submit(operation);
    int successes = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
        + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
    pool.shutdownNow();

    assertEquals(1, successes);
    assertEquals("pending", status(id));
    assertEquals(1, replayCount(id));
  }

  private DurableEventMessage message(String id, Instant occurredAt) {
    return new DurableEventMessage(
        id,
        "catalog.release-went-live",
        1,
        "release",
        "release-" + id,
        java.util.Map.of(
            "releaseId", "release-" + id,
            "artistId", "artist-" + id,
            "wentLiveAt", occurredAt.toString()),
        occurredAt);
  }

  private static Set<String> ids(List<DurableEventEnvelope> events) {
    Set<String> ids = new HashSet<>();
    events.forEach(event -> ids.add(event.id()));
    return ids;
  }

  private static String unique(String prefix) {
    return "outbox-it-" + prefix + "-" + System.nanoTime();
  }

  private int count(String id) {
    return number("select count(*) from platform_outbox_event where id = ?", id);
  }

  private int receiptCount(String id, String consumer) {
    return number(
        "select count(*) from platform_inbox_receipt where event_id = ? and consumer_name = ?",
        id,
        consumer);
  }

  private int replayCount(String id) {
    return number("select replay_count from platform_outbox_event where id = ?", id);
  }

  private int number(String sql, String... parameters) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < parameters.length; i++) statement.setString(i + 1, parameters[i]);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getInt(1);
      }
    } catch (Exception failure) {
      throw new RuntimeException(failure);
    }
  }

  private String status(String id) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select status from platform_outbox_event where id = ?")) {
      statement.setString(1, id);
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next());
        return result.getString(1);
      }
    } catch (Exception failure) {
      throw new RuntimeException(failure);
    }
  }
}
