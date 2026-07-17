package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.catalog.application.service.DeleteReleaseService;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseLiveException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link DeleteReleaseService}. Covers LLFR-CATALOG-02.3 acceptance criteria. No
 * framework; plain JUnit 5.
 */
@Tag("unit")
class DeleteReleaseServiceTest {

  private FakeCatalogRepository repo;
  private DeleteReleaseService service;

  private static final ArtistId ARTIST = new ArtistId("artist-del");
  private static final String RELEASE_ID = "release-del-1";

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    service = new DeleteReleaseService(
        repo, FakeIds.sequential("del"), FakeClock.fixed(), new FakeAuditWriter());
  }

  /** LLFR-CATALOG-02.3: deleting a live release → 409 RELEASE_LIVE. */
  @Test
  void delete_live_release_throws_ReleaseLive() {
    Release live = Release.reconstitute(
        RELEASE_ID, ARTIST.value(), "Live Album",
        ReleaseType.album, ReleaseStatus.live, Visibility.PUBLIC,
        null, null, 5000L,
        java.time.Instant.now(), java.time.Instant.now(), List.of(), null, null);
    repo.addRelease(live);

    assertThrows(ReleaseLiveException.class,
        () -> service.delete(new ReleaseId(RELEASE_ID), ARTIST));
  }

  /** LLFR-CATALOG-02.3: deleting a scheduled release → 409 RELEASE_LIVE. */
  @Test
  void delete_scheduled_release_throws_ReleaseLive() {
    Release scheduled = Release.reconstitute(
        "release-del-sched", ARTIST.value(), "Sched Album",
        ReleaseType.album, ReleaseStatus.scheduled, Visibility.PUBLIC,
        null, null, 5000L,
        java.time.Instant.now(), java.time.Instant.now(), List.of(), null, null);
    repo.addRelease(scheduled);

    assertThrows(ReleaseLiveException.class,
        () -> service.delete(new ReleaseId("release-del-sched"), ARTIST));
  }

  /** LLFR-CATALOG-02.3: deleting an in_review release succeeds. */
  @Test
  void delete_in_review_release_succeeds() {
    Release inReview = Release.reconstitute(
        RELEASE_ID, ARTIST.value(), "My EP",
        ReleaseType.ep, ReleaseStatus.in_review, Visibility.PUBLIC,
        null, null, 2000L,
        java.time.Instant.now(), java.time.Instant.now(), List.of(), null, null);
    repo.addRelease(inReview);

    service.delete(new ReleaseId(RELEASE_ID), ARTIST); // should not throw

    // Release no longer in repo
    assert repo.findRelease(new ReleaseId(RELEASE_ID)).isEmpty();
  }
}
