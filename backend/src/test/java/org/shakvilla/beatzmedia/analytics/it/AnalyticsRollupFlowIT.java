package org.shakvilla.beatzmedia.analytics.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.analytics.adapter.in.events.FollowedObserver;
import org.shakvilla.beatzmedia.analytics.adapter.in.events.PlayRecordedObserver;
import org.shakvilla.beatzmedia.analytics.adapter.in.events.SaleRecordedObserver;
import org.shakvilla.beatzmedia.analytics.adapter.in.events.TipReceivedObserver;
import org.shakvilla.beatzmedia.analytics.application.port.in.AnalyticsReader;
import org.shakvilla.beatzmedia.analytics.application.port.in.RollupWindow;
import org.shakvilla.beatzmedia.analytics.application.service.AudienceRollupJob;
import org.shakvilla.beatzmedia.analytics.application.service.SalesRollupJob;
import org.shakvilla.beatzmedia.analytics.domain.AnalyticsRange;
import org.shakvilla.beatzmedia.analytics.domain.MetricKey;
import org.shakvilla.beatzmedia.analytics.domain.StudioInsights;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.commerce.domain.SaleRecorded;
import org.shakvilla.beatzmedia.library.domain.FollowKind;
import org.shakvilla.beatzmedia.library.domain.Followed;
import org.shakvilla.beatzmedia.payments.domain.TipReceived;
import org.shakvilla.beatzmedia.playback.domain.PlayRecorded;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end integration for WU-ANA-1 (LLFR-ANALYTICS-01.1) against a real Testcontainers Postgres.
 * Drives the REAL pipeline: canonical domain events (as commerce/payments/playback/library would
 * fire them) -> analytics' CDI observers -> staged facts -> {@link SalesRollupJob}/
 * {@link AudienceRollupJob} -> {@link AnalyticsReader#studioInsights}.
 *
 * <p><strong>Faithful to production transaction boundaries.</strong> Each phase runs in its OWN
 * committed transaction, exactly as production does — the seed commits before the (AFTER_SUCCESS,
 * own-transaction) observers run so play resolution can see the track; the rollup jobs commit their
 * upserts; and the studio read happens in a FRESH transaction (as a studio HTTP request would),
 * never sharing a persistence context with the writing job. Sharing one transaction would let the
 * reader observe a stale first-level-cache entity from the job's native upserts — a test artifact
 * that never occurs in production, where the scheduled job and the read are always separate requests.
 *
 * <p><strong>Proves the AC</strong> (BACKEND-PRD.md LLFR-ANALYTICS-01.1): given seeded settlement/
 * play/follow events, {@code studioInsights(artist, 28d)} returns consistent KPIs where
 * {@code Σ MetricSeries.current == Σ sales_rollup/audience_rollup totals over the window}, and
 * that re-running the rollup job is idempotent (no double counting on redelivery).
 */
@QuarkusTest
@Tag("integration")
class AnalyticsRollupFlowIT {

  @Inject SaleRecordedObserver saleRecordedObserver;
  @Inject TipReceivedObserver tipReceivedObserver;
  @Inject PlayRecordedObserver playRecordedObserver;
  @Inject FollowedObserver followedObserver;

  @Inject SalesRollupJob salesRollupJob;
  @Inject AudienceRollupJob audienceRollupJob;
  @Inject AnalyticsReader analyticsReader;

  @Inject EntityManager em;

  @Test
  void seededEvents_rollupJob_thenStudioInsights28d_returnsConsistentKpis() {
    String artistId = "it-artist-" + System.nanoTime();
    ArtistId artist = new ArtistId(artistId);
    Instant occurredAt = Instant.now().minusSeconds(60);

    // Seed catalog rows in a COMMITTED transaction so that play resolution — which runs in the play
    // observer's own transaction, exactly as the AFTER_SUCCESS observer would in production — can see
    // the track via catalog's GetTrack input port.
    QuarkusTransaction.requiringNew().run(() -> seedTrackForArtist("it-track-1", artistId));

    // Each observer is @Transactional(REQUIRES_NEW) and commits its own staged fact — mirroring the
    // AFTER_SUCCESS CDI observers firing after the producing transaction has committed in production.
    //
    // THREE sales and TWO tips fall in the SAME (artist, bucket) — this exercises multi-fact
    // accumulation. A single fact per bucket would not catch a read-your-writes bug where the job
    // reads a stale rollup between successive upserts and undercounts (see the native-read note on
    // JpaSalesRollupRepository#find). Totals still come to 1500 sales / 450 tips.
    saleRecordedObserver.onSaleRecorded(new SaleRecorded("order-1", artistId, 500L, "GHS", occurredAt));
    saleRecordedObserver.onSaleRecorded(new SaleRecorded("order-2", artistId, 500L, "GHS", occurredAt));
    saleRecordedObserver.onSaleRecorded(new SaleRecorded("order-3", artistId, 500L, "GHS", occurredAt));
    tipReceivedObserver.onTipReceived(
        new TipReceived("intent-1", "fan-1", artistId, 250L, 200L, 50L, "GHS", occurredAt));
    tipReceivedObserver.onTipReceived(
        new TipReceived("intent-2", "fan-1", artistId, 300L, 250L, 50L, "GHS", occurredAt));
    playRecordedObserver.onPlayRecorded(
        new PlayRecorded("it-track-1", "listener-1", occurredAt, "full", "player"));
    playRecordedObserver.onPlayRecorded(
        new PlayRecorded("it-track-1", "listener-2", occurredAt, "full", "player"));
    playRecordedObserver.onPlayRecorded(
        new PlayRecorded("it-track-1", null, occurredAt, "full", "player")); // anonymous play
    followedObserver.onFollowed(new Followed("fan-2", FollowKind.artist, artistId, occurredAt));
    followedObserver.onFollowed(new Followed("fan-3", FollowKind.artist, artistId, occurredAt));
    followedObserver.onFollowed(new Followed("fan-4", FollowKind.playlist, "some-playlist", occurredAt));

    // Run the rollup jobs (each @Transactional, commits its own upserts) — mirrors the every=5m tick.
    salesRollupJob.run(new RollupWindow(Instant.now()));
    audienceRollupJob.run(new RollupWindow(Instant.now()));

    // Read insights in a FRESH transaction, exactly as a studio HTTP request would.
    StudioInsights insights =
        QuarkusTransaction.requiringNew()
            .call(() -> analyticsReader.studioInsights(artist, AnalyticsRange.TWENTY_EIGHT_DAYS));

    assertEquals(1500L, insights.metrics().get(MetricKey.SALES).total());
    assertEquals(450L, insights.metrics().get(MetricKey.TIPS).total());
    assertEquals(3L, insights.metrics().get(MetricKey.STREAMS).total(), "3 counted plays incl. 1 anonymous");
    assertEquals(2L, insights.metrics().get(MetricKey.FOLLOWERS).total(), "2 artist follows; playlist follow excluded");

    // Consistency invariant: Σ current == the total reported (and matches the raw rollup row).
    long sumCurrentSales =
        insights.metrics().get(MetricKey.SALES).current().stream().mapToLong(Long::longValue).sum();
    assertEquals(insights.metrics().get(MetricKey.SALES).total(), sumCurrentSales);

    long rawSalesMinor =
        QuarkusTransaction.requiringNew().call(() -> salesMinorFromRollup(artistId, occurredAt));
    assertEquals(rawSalesMinor, insights.metrics().get(MetricKey.SALES).total(), "reader total must match the rollup row");

    // Idempotency: re-run the job with nothing new -> rollup rows unchanged (no double counting).
    salesRollupJob.run(new RollupWindow(Instant.now()));
    audienceRollupJob.run(new RollupWindow(Instant.now()));
    StudioInsights insightsAfterRerun =
        QuarkusTransaction.requiringNew()
            .call(() -> analyticsReader.studioInsights(artist, AnalyticsRange.TWENTY_EIGHT_DAYS));
    assertEquals(
        1500L,
        insightsAfterRerun.metrics().get(MetricKey.SALES).total(),
        "re-running the rollup job must not double-count");
    assertEquals(
        450L,
        insightsAfterRerun.metrics().get(MetricKey.TIPS).total(),
        "re-running the rollup job must not double-count tips");
    assertEquals(3L, insightsAfterRerun.metrics().get(MetricKey.STREAMS).total());

    assertTrue(rawSalesMinor > 0);
  }

  private void seedTrackForArtist(String trackId, String artistId) {
    // Minimal catalog rows so GetTrack (via ArtistResolverAdapter) resolves trackId -> artistId
    // in-process, exactly as production does — never a direct table read from analytics.
    em.createNativeQuery(
            "INSERT INTO artist_profile (id, name, image, verified, followers, monthly_listeners, bio) "
                + "VALUES (:id, 'IT Artist', 'img.png', true, 0, 0, 'bio') ON CONFLICT (id) DO NOTHING")
        .setParameter("id", artistId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, duration_sec, image, ownership, "
                + " plays, audio_url, quality, year) "
                + "VALUES (:id, 'IT Track', :artist, 'IT Artist', 180, 'img.png', 'free', "
                + " 0, 'audio.mp3', 'standard', 2026) "
                + "ON CONFLICT (id) DO NOTHING")
        .setParameter("id", trackId)
        .setParameter("artist", artistId)
        .executeUpdate();
  }

  private long salesMinorFromRollup(String artistId, Instant occurredAt) {
    LocalDate bucket = occurredAt.atZone(ZoneOffset.UTC).toLocalDate();
    Object v =
        em.createNativeQuery(
                "SELECT sales_minor FROM sales_rollup WHERE artist_id = :artist AND bucket = :bucket AND grain = 'DAILY'")
            .setParameter("artist", artistId)
            .setParameter("bucket", bucket)
            .getResultList()
            .stream()
            .findFirst()
            .orElse(0L);
    return ((Number) v).longValue();
  }
}
