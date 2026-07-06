package org.shakvilla.beatzmedia.analytics.application.service;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.analytics.application.port.in.RollupJob;
import org.shakvilla.beatzmedia.analytics.application.port.in.RollupResult;
import org.shakvilla.beatzmedia.analytics.application.port.in.RollupWindow;
import org.shakvilla.beatzmedia.analytics.application.port.out.SaleFactRepository;
import org.shakvilla.beatzmedia.analytics.application.port.out.SalesRollupRepository;
import org.shakvilla.beatzmedia.analytics.application.port.out.TipFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.analytics.domain.RollupBucket;
import org.shakvilla.beatzmedia.analytics.domain.SaleFact;
import org.shakvilla.beatzmedia.analytics.domain.SalesRollup;
import org.shakvilla.beatzmedia.analytics.domain.TipFact;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/**
 * Maintains {@code sales_rollup} from analytics' own staged {@link SaleFact}/{@link TipFact} rows
 * (LLFR-ANALYTICS-01.1). Never reads a commerce/payments table — its sole inputs are the facts
 * appended by {@link SaleRecordedObserver}/{@link TipReceivedObserver}. Analytics ADD §4.1 / §8.3.
 *
 * <p><strong>Idempotent per window (ADD §4.1).</strong> Each unprocessed fact is folded into the
 * row for its bucket at ALL THREE grains (DAILY/WEEKLY/MONTHLY) via
 * {@link SalesRollupRepository#upsert}, keyed on the unique {@code (artist_id, bucket, grain)}
 * constraint, then the fact is marked processed. A fact is folded exactly once — re-running the job
 * with no new facts is a pure no-op (nothing left unprocessed), so re-running never double-counts.
 *
 * <p><strong>royaltyMinor is always 0</strong> (OQ-4: pure buy-to-own, no royalty model) — every
 * {@link SalesRollup} constructed here carries {@code royaltyMinor=0} and the domain constructor
 * itself rejects any other value.
 */
@ApplicationScoped
public class SalesRollupJob implements RollupJob {

  private final SaleFactRepository saleFacts;
  private final TipFactRepository tipFacts;
  private final SalesRollupRepository rollups;

  @Inject
  public SalesRollupJob(
      SaleFactRepository saleFacts, TipFactRepository tipFacts, SalesRollupRepository rollups) {
    this.saleFacts = saleFacts;
    this.tipFacts = tipFacts;
    this.rollups = rollups;
  }

  @Override
  public String name() {
    return "analytics.sales-rollup";
  }

  @Override
  @Transactional
  public RollupResult run(RollupWindow window) {
    List<SaleFact> unprocessedSales = saleFacts.findUnprocessed();
    List<TipFact> unprocessedTips = tipFacts.findUnprocessed();

    Set<String> touchedBuckets = new HashSet<>();

    for (SaleFact fact : unprocessedSales) {
      ArtistId artist = new ArtistId(fact.artistId());
      for (Grain grain : Grain.values()) {
        RollupBucket bucket =
            RollupBucket.of(fact.occurredAt().atZone(ZoneOffset.UTC).toLocalDate(), grain);
        SalesRollup current =
            rollups.find(artist, bucket.bucket(), grain).orElse(SalesRollup.zero(artist, bucket));
        rollups.upsert(current.plusSale(fact.grossMinor()));
        touchedBuckets.add(artist.value() + "|" + bucket.bucket() + "|" + grain);
      }
    }

    for (TipFact fact : unprocessedTips) {
      ArtistId artist = new ArtistId(fact.artistId());
      for (Grain grain : Grain.values()) {
        RollupBucket bucket =
            RollupBucket.of(fact.occurredAt().atZone(ZoneOffset.UTC).toLocalDate(), grain);
        SalesRollup current =
            rollups.find(artist, bucket.bucket(), grain).orElse(SalesRollup.zero(artist, bucket));
        rollups.upsert(current.plusTip(fact.creatorShareMinor()));
        touchedBuckets.add(artist.value() + "|" + bucket.bucket() + "|" + grain);
      }
    }

    List<String> saleIds = new ArrayList<>(unprocessedSales.size());
    unprocessedSales.forEach(f -> saleIds.add(f.id()));
    saleFacts.markProcessed(saleIds);

    List<String> tipIds = new ArrayList<>(unprocessedTips.size());
    unprocessedTips.forEach(f -> tipIds.add(f.id()));
    tipFacts.markProcessed(tipIds);

    return new RollupResult(unprocessedSales.size() + unprocessedTips.size(), touchedBuckets.size());
  }
}
