package org.shakvilla.beatzmedia.engagement.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.engagement.application.port.in.GetDiscoveryEngagementMetrics;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Tag("integration")
class DiscoveryEngagementMetricsIT {

  @Inject EntityManager em;
  @Inject GetDiscoveryEngagementMetrics metrics;

  @Test
  @TestTransaction
  void countsDistinctListenersCapsRepeatsAndRespectsTheRequestedIdentity() {
    String suffix = Long.toString(System.nanoTime());
    String wanted = "track-wanted-" + suffix;
    String other = "track-other-" + suffix;
    Instant now = Instant.parse("2026-11-05T12:00:00Z");
    session("listener-a-" + suffix, now);
    session("listener-b-" + suffix, now);
    for (int i = 0; i < 8; i++) {
      checkpoint("listener-a-" + suffix, wanted, "qualified-play", i + 1,
          now.minusSeconds(3600));
    }
    checkpoint("listener-b-" + suffix, wanted, "qualified-play", 1,
        now.minusSeconds(3600));
    checkpoint("listener-b-" + suffix, wanted, "qualified-play", 2,
        now.minusSeconds(3 * 86400L));
    checkpoint("listener-b-" + suffix, wanted, "completion", 3,
        now.minusSeconds(3600));
    checkpoint("listener-b-" + suffix, other, "qualified-play", 4,
        now.minusSeconds(3600));

    var rows = metrics.get(List.of(new GetDiscoveryEngagementMetrics.ContentRef("track", wanted)), now);
    assertEquals(1, rows.size());
    var row = rows.getFirst();
    assertEquals(7, row.qualifiedPlays28d());
    assertEquals(6, row.qualifiedPlays24h());
    assertEquals(1, row.qualifiedPlaysPrevious7d());
    assertEquals(2, row.uniqueListeners28d());
    assertEquals(2, row.uniqueListeners24h());
    assertEquals(1, row.completions28d());
  }

  @Test
  @TestTransaction
  void candidateSelectionUnionsSustainedAndRecentWindows() {
    String suffix = Long.toString(System.nanoTime());
    String sustained = "sustained-" + suffix;
    String breakout = "breakout-" + suffix;
    Instant now = Instant.parse("2026-11-05T12:00:00Z");
    rollup("2026-10-27", sustained, 100);
    rollup("2026-11-05", breakout, 8);

    var candidates = metrics.candidates(now, 1);
    assertEquals(2, candidates.size());
    assertEquals(sustained, candidates.getFirst().id());
    assertEquals(breakout, candidates.get(1).id());
  }

  @Test
  @TestTransaction
  void groupCountsAListenerOnceAcrossTwoTracks() {
    String suffix = Long.toString(System.nanoTime());
    String first = "group-first-" + suffix;
    String second = "group-second-" + suffix;
    String listenerA = "group-a-" + suffix;
    String listenerB = "group-b-" + suffix;
    Instant now = Instant.parse("2026-11-05T12:00:00Z");
    session(listenerA, now);
    session(listenerB, now);
    checkpoint(listenerA, first, "qualified-play", 1, now.minusSeconds(300));
    checkpoint(listenerA, second, "qualified-play", 2, now.minusSeconds(200));
    checkpoint(listenerB, second, "qualified-play", 1, now.minusSeconds(100));

    var group = metrics.group(List.of(
        new GetDiscoveryEngagementMetrics.ContentRef("track", first),
        new GetDiscoveryEngagementMetrics.ContentRef("track", second)), now);
    assertEquals(3, group.qualifiedPlays28d());
    assertEquals(2, group.uniqueListeners28d());
    assertEquals(2, group.uniqueListeners24h());
  }

  private void rollup(String day, String contentId, long plays) {
    em.createNativeQuery("""
        insert into engagement_daily_rollup (day, content_kind, content_id, qualified_plays)
        values (cast(:day as date), 'track', :content, :plays)
        """)
        .setParameter("day", day)
        .setParameter("content", contentId)
        .setParameter("plays", plays)
        .executeUpdate();
  }

  private void session(String id, Instant now) {
    em.createNativeQuery("insert into listening_session (id, account_id, started_at) values (:id, :account, :now)")
        .setParameter("id", id).setParameter("account", id).setParameter("now", now)
        .executeUpdate();
  }

  private void checkpoint(String sessionId, String contentId, String type, int sequence, Instant at) {
    em.createNativeQuery("""
        insert into listening_checkpoint (id, session_id, account_id, sequence, content_kind,
          content_id, event_type, position_seconds, duration_seconds, listened_delta_seconds,
          effective, received_at)
        values (:id, :session, :account, :sequence, 'track', :content, :type,
          30, 120, 30, true, :at)
        """)
        .setParameter("id", sessionId + ":" + sequence)
        .setParameter("session", sessionId)
        .setParameter("account", sessionId)
        .setParameter("sequence", sequence)
        .setParameter("content", contentId)
        .setParameter("type", type)
        .setParameter("at", at)
        .executeUpdate();
  }
}
