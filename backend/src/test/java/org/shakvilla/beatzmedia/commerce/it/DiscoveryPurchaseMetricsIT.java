package org.shakvilla.beatzmedia.commerce.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.in.GetDiscoveryPurchaseMetrics;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Tag("integration")
class DiscoveryPurchaseMetricsIT {

  @Inject EntityManager em;
  @Inject GetDiscoveryPurchaseMetrics metrics;

  @Test
  @TestTransaction
  void countsDistinctSettledOrdersByPublishedCardAndExcludesRefundsAndPending() {
    String suffix = UUID.randomUUID().toString();
    String album = "album-" + suffix;
    String track = "track-" + suffix;
    String show = "show-" + suffix;
    String episode = "episode-" + suffix;
    Instant now = Instant.parse("2026-11-06T09:00:00Z");

    String albumOrder = order("paid");
    line(albumOrder, "album", album);
    line(albumOrder, "track", track);
    posting(albumOrder, now);

    String podcastOrder = order("fulfilled");
    line(podcastOrder, "season-pass", show);
    line(podcastOrder, "episode", episode);
    posting(podcastOrder, now);

    String refundedOrder = order("refunded");
    line(refundedOrder, "album-rest", album);
    posting(refundedOrder, now);

    String pendingOrder = order("pending");
    line(pendingOrder, "album", album);

    String oldOrder = order("paid");
    line(oldOrder, "album", album);
    posting(oldOrder, now.minusSeconds(30L * 86400));

    Instant since = now.minusSeconds(28L * 86400);
    Instant asOf = now.plusSeconds(1);
    assertEquals(1, metrics.count("album", album, List.of(track), since, asOf));
    assertEquals(1, metrics.count("ep", album, List.of(track), since, asOf));
    assertEquals(1, metrics.count("single", track, List.of(track), since, asOf));
    assertEquals(1, metrics.count("podcast", show, List.of(episode), since, asOf));
    assertEquals(0, metrics.count("album", album, List.of(track), now.plusSeconds(1),
        now.plusSeconds(2)));
  }

  private String order(String status) {
    String id = UUID.randomUUID().toString();
    em.createNativeQuery("insert into \"order\" (id, account_id, reference, status,"
        + " subtotal_minor, fee_minor, total_minor, idempotency_key, request_hash)"
        + " values (:id, :account, :reference, :status, 100, 0, 100, :idem, :hash)")
        .setParameter("id", id)
        .setParameter("account", UUID.randomUUID().toString())
        .setParameter("reference", "BZ-2026-" + id.substring(0, 8))
        .setParameter("status", status)
        .setParameter("idem", id)
        .setParameter("hash", "test-" + id)
        .executeUpdate();
    return id;
  }

  private void line(String orderId, String kind, String refId) {
    em.createNativeQuery("insert into order_line (id, order_id, kind, ref_id, title,"
        + " unit_price_minor, qty) values (:id, :orderId, :kind, :refId, 'Test', 100, 1)")
        .setParameter("id", UUID.randomUUID().toString())
        .setParameter("orderId", orderId)
        .setParameter("kind", kind)
        .setParameter("refId", refId)
        .executeUpdate();
  }

  private void posting(String orderId, Instant at) {
    em.createNativeQuery("insert into order_grant_posting (order_id, posted_at)"
        + " values (:orderId, :at)")
        .setParameter("orderId", orderId)
        .setParameter("at", at)
        .executeUpdate();
  }
}
