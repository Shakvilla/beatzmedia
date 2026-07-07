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
import org.shakvilla.beatzmedia.analytics.application.port.out.AudienceRollupRepository;
import org.shakvilla.beatzmedia.analytics.application.port.out.FollowFactRepository;
import org.shakvilla.beatzmedia.analytics.application.port.out.PlayFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.AudienceRollup;
import org.shakvilla.beatzmedia.analytics.domain.FollowFact;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.analytics.domain.PlayFact;
import org.shakvilla.beatzmedia.analytics.domain.RollupBucket;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/**
 * Maintains {@code audience_rollup} from analytics' own staged {@link PlayFact}/{@link FollowFact}
 * rows (LLFR-ANALYTICS-01.1). Never reads a playback/library table — its sole inputs are the facts
 * appended by {@link PlayRecordedObserver}/{@link FollowedObserver}. Analytics ADD §4.1 / §8.3.
 *
 * <p><strong>Idempotent per window.</strong> Same pattern as {@link SalesRollupJob}: each
 * unprocessed fact is folded into ALL THREE grains then marked processed, so a fact is folded
 * exactly once and a re-run with no new facts is a no-op.
 */
@ApplicationScoped
public class AudienceRollupJob implements RollupJob {

  private final PlayFactRepository playFacts;
  private final FollowFactRepository followFacts;
  private final AudienceRollupRepository rollups;

  @Inject
  public AudienceRollupJob(
      PlayFactRepository playFacts, FollowFactRepository followFacts, AudienceRollupRepository rollups) {
    this.playFacts = playFacts;
    this.followFacts = followFacts;
    this.rollups = rollups;
  }

  @Override
  public String name() {
    return "analytics.audience-rollup";
  }

  @Override
  @Transactional
  public RollupResult run(RollupWindow window) {
    List<PlayFact> unprocessedPlays = playFacts.findUnprocessed();
    List<FollowFact> unprocessedFollows = followFacts.findUnprocessed();

    Set<String> touchedBuckets = new HashSet<>();

    for (PlayFact fact : unprocessedPlays) {
      ArtistId artist = new ArtistId(fact.artistId());
      for (Grain grain : Grain.values()) {
        RollupBucket bucket =
            RollupBucket.of(fact.occurredAt().atZone(ZoneOffset.UTC).toLocalDate(), grain);
        AudienceRollup current =
            rollups.find(artist, bucket.bucket(), grain).orElse(AudienceRollup.zero(artist, bucket));
        rollups.upsert(current.plusPlay());
        touchedBuckets.add(artist.value() + "|" + bucket.bucket() + "|" + grain);
      }
    }

    for (FollowFact fact : unprocessedFollows) {
      ArtistId artist = new ArtistId(fact.artistId());
      for (Grain grain : Grain.values()) {
        RollupBucket bucket =
            RollupBucket.of(fact.occurredAt().atZone(ZoneOffset.UTC).toLocalDate(), grain);
        AudienceRollup current =
            rollups.find(artist, bucket.bucket(), grain).orElse(AudienceRollup.zero(artist, bucket));
        rollups.upsert(current.plusFollower());
        touchedBuckets.add(artist.value() + "|" + bucket.bucket() + "|" + grain);
      }
    }

    List<String> playIds = new ArrayList<>(unprocessedPlays.size());
    unprocessedPlays.forEach(f -> playIds.add(f.id()));
    playFacts.markProcessed(playIds);

    List<String> followIds = new ArrayList<>(unprocessedFollows.size());
    unprocessedFollows.forEach(f -> followIds.add(f.id()));
    followFacts.markProcessed(followIds);

    return new RollupResult(unprocessedPlays.size() + unprocessedFollows.size(), touchedBuckets.size());
  }
}
