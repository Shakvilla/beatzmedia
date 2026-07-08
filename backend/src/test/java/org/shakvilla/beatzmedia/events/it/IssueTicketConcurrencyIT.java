package org.shakvilla.beatzmedia.events.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.events.application.port.in.IssueTicket;
import org.shakvilla.beatzmedia.events.application.port.in.IssueTicketCommand;
import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.events.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.events.domain.OrderId;
import org.shakvilla.beatzmedia.events.domain.TicketTierId;
import org.shakvilla.beatzmedia.events.domain.TierSoldOutException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Proves INV-EVT-1 / OQ-11: given a tier with capacity N and N+M concurrent settlement attempts,
 * EXACTLY N tickets are minted, {@code sold == capacity}, and the M excess fail {@code
 * TIER_SOLD_OUT} — no oversell, under real Postgres row locking (Testcontainers). Events ADD §9 /
 * §11 (OQ-11 acceptance).
 */
@QuarkusTest
@Tag("integration")
class IssueTicketConcurrencyIT {

  private static final int CAPACITY = 5;
  private static final int EXCESS = 5;
  private static final int ATTEMPTS = CAPACITY + EXCESS;

  @Inject EntityManager em;
  @Inject IssueTicket issueTicket;

  private String eventId;
  private String tierId;

  @BeforeEach
  @Transactional
  void seed() {
    long n = System.nanoTime();
    eventId = "evt-cc-" + n;
    tierId = eventId + "-regular";

    em.createNativeQuery(
            "INSERT INTO event (id, title, artist_name, image, event_at, venue, city, category,"
                + " popularity) VALUES (:id, 'Concurrency Event', 'Artist', 'img.png', now() +"
                + " interval '5 days', 'Venue', 'Accra', 'Concert', 10)")
        .setParameter("id", eventId)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO ticket_tier (id, event_id, name, price_minor, capacity, sold)"
                + " VALUES (:id, :eventId, 'Regular', 10000, :capacity, 0)")
        .setParameter("id", tierId)
        .setParameter("eventId", eventId)
        .setParameter("capacity", CAPACITY)
        .executeUpdate();
  }

  @Test
  void concurrentIssuance_exceedingCapacity_mintsExactlyCapacityTickets_noOversell()
      throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
    CountDownLatch ready = new CountDownLatch(ATTEMPTS);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger succeeded = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();

    List<Callable<Void>> tasks = new ArrayList<>();
    for (int i = 0; i < ATTEMPTS; i++) {
      String orderId = "cc-order-" + i;
      tasks.add(
          () -> {
            ready.countDown();
            start.await();
            try {
              issueTicket.issue(
                  new IssueTicketCommand(
                      new EventId(eventId),
                      new TicketTierId(tierId),
                      new OrderId(orderId),
                      new AccountId("acct-cc-" + orderId),
                      "Concurrency Fan",
                      1,
                      new IdempotencyKey("idem-" + orderId)));
              succeeded.incrementAndGet();
            } catch (TierSoldOutException e) {
              rejected.incrementAndGet();
            }
            return null;
          });
    }

    List<Future<Void>> futures = new ArrayList<>();
    for (Callable<Void> task : tasks) {
      futures.add(pool.submit(task));
    }
    ready.await(10, TimeUnit.SECONDS); // all threads parked at the barrier
    start.countDown(); // release them all at once to maximize contention
    for (Future<Void> f : futures) {
      f.get(30, TimeUnit.SECONDS);
    }
    pool.shutdown();

    assertEquals(CAPACITY, succeeded.get(), "exactly capacity tickets should be minted");
    assertEquals(EXCESS, rejected.get(), "the excess attempts must fail TIER_SOLD_OUT");

    int soldInDb =
        ((Number)
                em.createNativeQuery("SELECT sold FROM ticket_tier WHERE id = :id")
                    .setParameter("id", tierId)
                    .getSingleResult())
            .intValue();
    assertEquals(CAPACITY, soldInDb, "sold must equal capacity — no oversell (INV-EVT-1)");

    long ticketCount =
        ((Number)
                em.createNativeQuery("SELECT COUNT(*) FROM ticket WHERE tier_id = :id")
                    .setParameter("id", tierId)
                    .getSingleResult())
            .longValue();
    assertEquals(CAPACITY, ticketCount, "exactly capacity ticket rows must be minted");
  }
}
