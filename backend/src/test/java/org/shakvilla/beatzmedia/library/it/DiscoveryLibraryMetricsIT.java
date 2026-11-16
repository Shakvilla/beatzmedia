package org.shakvilla.beatzmedia.library.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.library.application.port.in.GetDiscoveryLibraryMetrics;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Tag("integration")
class DiscoveryLibraryMetricsIT {

  @Inject EntityManager em;
  @Inject GetDiscoveryLibraryMetrics metrics;

  @Test
  @TestTransaction
  void countsRecentSavesAndCreatorFollowsWithoutExposingAccounts() {
    String suffix = UUID.randomUUID().toString();
    Instant now = Instant.parse("2026-11-06T09:00:00Z");
    UUID fan = UUID.randomUUID();
    insert("liked_track", "track_id", "track-" + suffix, fan, now);
    insert("saved_album", "album_id", "album-" + suffix, fan, now);
    insert("followed_artist", "artist_id", "artist-" + suffix, fan, now);
    insert("followed_show", "show_id", "show-" + suffix, fan, now);

    assertEquals(new GetDiscoveryLibraryMetrics.Signals(1, 1),
        metrics.get("single", "track-" + suffix, "artist-" + suffix,
            now.minusSeconds(3600), now.plusSeconds(1)));
    assertEquals(new GetDiscoveryLibraryMetrics.Signals(1, 1),
        metrics.get("ep", "album-" + suffix, "artist-" + suffix,
            now.minusSeconds(3600), now.plusSeconds(1)));
    assertEquals(new GetDiscoveryLibraryMetrics.Signals(0, 1),
        metrics.get("podcast", "show-" + suffix, "show-" + suffix,
            now.minusSeconds(3600), now.plusSeconds(1)));
    assertEquals(new GetDiscoveryLibraryMetrics.Signals(0, 0),
        metrics.get("album", "album-" + suffix, "artist-" + suffix,
            now.plusSeconds(1), now.plusSeconds(2)));
    assertEquals(new GetDiscoveryLibraryMetrics.Signals(0, 0),
        metrics.get("single", "track-" + suffix, "artist-" + suffix,
            now.minusSeconds(3600), now));
  }

  private void insert(String table, String column, String target, UUID fan, Instant at) {
    em.createNativeQuery("insert into " + table + " (account_id, " + column
        + ", created_at) values (:account, :target, :at)")
        .setParameter("account", fan)
        .setParameter("target", target)
        .setParameter("at", at)
        .executeUpdate();
  }
}
