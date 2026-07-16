package org.shakvilla.beatzmedia.search.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;
import org.shakvilla.beatzmedia.search.application.port.in.QueryService;
import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Guards the {@code search.reindex} scheduler wiring (WU-SRCH-2 Task 5).
 *
 * <p>{@code SchedulerRegistry.runWithLock} resolves the {@link ScheduledJob} to run by looking up
 * its {@code jobName()} in a CDI-built index. {@code SchedulerRegistry}'s private
 * {@code JOB_SEARCH_REINDEX} constant and {@code ReindexJob#jobName()} independently hard-code the
 * string {@code "search.reindex"}; nothing at compile time keeps them in sync. If either drifts
 * (e.g. a typo'd {@code "search-reindex"}), the lookup misses, {@code runWithLock} logs at DEBUG
 * and returns — the tick becomes a silent, permanent no-op. That is exactly the bug this work unit
 * exists to fix (the index went stale for the same reason), so a regression here must fail loudly.
 *
 * <p>{@link SearchBackfillIT} does not cover this: it calls {@code ReindexUseCase.reindex(null)}
 * directly and never goes through {@code SchedulerRegistry} or CDI job discovery, so it passes
 * identically whether or not {@code ReindexJob} exists or is named correctly. This test instead
 * injects {@code Instance<ScheduledJob>} — the same CDI collection {@code SchedulerRegistry} injects
 * — in the full Quarkus container, so it proves real bean discoverability rather than a hand-wired
 * fake.
 *
 * <p>Kept as its own IT (rather than folded into {@code SearchBackfillIT}) because it asserts a
 * different concern — CDI/scheduler wiring — from {@code SearchBackfillIT}'s domain-level reindex
 * behaviour; keeping them separate means a wiring regression and a domain regression fail with
 * distinct, unambiguous test names.
 */
@QuarkusTest
@Tag("integration")
class ReindexJobRegistrationIT {

  private static final String SEARCH_REINDEX_JOB_NAME = "search.reindex";

  @Inject
  Instance<ScheduledJob> jobBeans;

  @Inject
  QueryService queryService;

  @Test
  void exactlyOneScheduledJobBean_isRegisteredUnderTheSearchReindexName() {
    List<ScheduledJob> matches = new ArrayList<>();
    for (ScheduledJob job : jobBeans) {
      if (SEARCH_REINDEX_JOB_NAME.equals(job.jobName())) {
        matches.add(job);
      }
    }

    assertEquals(1, matches.size(),
        "expected exactly one ScheduledJob bean named '" + SEARCH_REINDEX_JOB_NAME
            + "' — this is the name SchedulerRegistry's 10-minute search-reindex tick looks up. "
            + "Zero matches means the tick silently no-ops forever (the WU-SRCH-2 bug); two or "
            + "more means which bean runs for the tick is undefined. Found: " + matches.size());
  }

  @Test
  void resolvedSearchReindexJob_runOnce_populatesTheIndexEndToEnd() {
    ScheduledJob resolved = null;
    for (ScheduledJob job : jobBeans) {
      if (SEARCH_REINDEX_JOB_NAME.equals(job.jobName())) {
        resolved = job;
        break;
      }
    }
    assertNotNull(resolved,
        "no ScheduledJob bean registered under '" + SEARCH_REINDEX_JOB_NAME + "'");

    ScheduledJob searchReindexJob = resolved;
    assertDoesNotThrow(searchReindexJob::runOnce,
        "the resolved 'search.reindex' job must run without throwing");

    SearchResults results = queryService.search(SearchQuery.of("sherif"));
    assertTrue(
        results.artists().stream().anyMatch(h -> h.entityId().equals("black-sherif")),
        "expected runOnce() on the CDI-resolved 'search.reindex' bean to have indexed the seeded "
            + "artist 'black-sherif', proving the real scheduler pathway end to end");
  }
}
