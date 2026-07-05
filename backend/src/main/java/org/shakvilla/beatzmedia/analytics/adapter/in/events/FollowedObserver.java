package org.shakvilla.beatzmedia.analytics.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.analytics.application.port.out.FollowFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.FollowFact;
import org.shakvilla.beatzmedia.library.domain.FollowKind;
import org.shakvilla.beatzmedia.library.domain.Followed;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * CDI event observer that turns library's {@link Followed} into a staged {@link FollowFact}
 * (LLFR-ANALYTICS-01.1), for {@code kind=artist} follows only (playlist/show follows do not feed
 * the artist audience rollup). Same no-cross-module-reads / {@code AFTER_SUCCESS} guarantees as
 * {@link SaleRecordedObserver}. Analytics ADD §4.1 / §8.3.
 */
@ApplicationScoped
public class FollowedObserver {

  private final FollowFactRepository followFacts;
  private final IdGenerator ids;

  @Inject
  public FollowedObserver(FollowFactRepository followFacts, IdGenerator ids) {
    this.followFacts = followFacts;
    this.ids = ids;
  }

  public void onFollowed(@Observes(during = TransactionPhase.AFTER_SUCCESS) Followed event) {
    if (event.kind() != FollowKind.artist) {
      return;
    }
    followFacts.append(FollowFact.unprocessed(ids.newId(), event.targetId(), event.at()));
  }
}
