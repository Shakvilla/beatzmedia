package org.shakvilla.beatzmedia.catalog.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.catalog.application.port.in.PublishRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.PublishRelease.ReleaseTransition;
import org.shakvilla.beatzmedia.catalog.application.port.in.RunGoLiveSweep;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Application service for {@link RunGoLiveSweep}. Enumerates due {@code scheduled} releases via
 * {@link CatalogRepository#dueScheduled} and delegates each transition to {@link PublishRelease}
 * (the single source of FSM truth). INV-7 / LLFR-PLATFORM-01.2. Catalog ADD §4.1.
 */
@ApplicationScoped
public class RunGoLiveSweepService implements RunGoLiveSweep {

  private static final Logger LOG = Logger.getLogger(RunGoLiveSweepService.class);

  private final CatalogRepository repo;
  private final PublishRelease publishRelease;
  private final Clock clock;

  @Inject
  public RunGoLiveSweepService(CatalogRepository repo, PublishRelease publishRelease, Clock clock) {
    this.repo = repo;
    this.publishRelease = publishRelease;
    this.clock = clock;
  }

  @Override
  @Transactional
  public int run() {
    Instant now = clock.now();
    List<Release> due = repo.dueScheduled(now);
    int transitioned = 0;
    for (Release release : due) {
      try {
        publishRelease.transition(
            new ReleaseId(release.getId()), ReleaseTransition.GO_LIVE, null, Optional.empty());
        transitioned++;
      } catch (IllegalTransitionException e) {
        // Already left 'scheduled' between enumeration and transition (e.g. raced by an admin
        // action) — safe to skip; not an error. One release's ineligibility never blocks the rest.
        LOG.debugf(
            "catalog.go-live: release %s no longer eligible (%s) — skipping",
            release.getId(), e.getMessage());
      }
    }
    return transitioned;
  }
}
