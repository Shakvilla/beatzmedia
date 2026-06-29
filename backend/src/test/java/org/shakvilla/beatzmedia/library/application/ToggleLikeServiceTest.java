package org.shakvilla.beatzmedia.library.application;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.service.ToggleLikeService;
import org.shakvilla.beatzmedia.library.domain.LikeSets;
import org.shakvilla.beatzmedia.library.domain.TargetNotFoundException;
import org.shakvilla.beatzmedia.library.fakes.FakeCatalogReader;
import org.shakvilla.beatzmedia.library.fakes.FakeCollectionRepository;

/** Unit tests for ToggleLikeService. LLFR-LIBRARY-01.2 / Library ADD §11. */
@Tag("unit")
class ToggleLikeServiceTest {

  FakeCollectionRepository repo;
  FakeCatalogReader catalog;
  ToggleLikeService service;

  private static final AccountId ACCOUNT = new AccountId("acct-1");

  @BeforeEach
  void setUp() {
    repo = new FakeCollectionRepository();
    catalog = new FakeCatalogReader();
    service = new ToggleLikeService(repo, catalog);
  }

  @Test
  void like_existingTrack_addsToLikedSet() {
    catalog.addTrack("track-1");
    service.like(ACCOUNT, "track-1");

    LikeSets sets = repo.likeSets(ACCOUNT);
    assertTrue(sets.likedTrackIds().contains("track-1"));
  }

  @Test
  void like_unknownTrack_throwsTargetNotFound() {
    var ex = assertThrows(TargetNotFoundException.class,
        () -> service.like(ACCOUNT, "unknown"));
    assertEquals("TRACK_NOT_FOUND", ex.code());
  }

  @Test
  void like_twice_idempotent_trackAppearsOnce() {
    catalog.addTrack("track-1");
    service.like(ACCOUNT, "track-1");
    service.like(ACCOUNT, "track-1");

    LikeSets sets = repo.likeSets(ACCOUNT);
    assertEquals(1, sets.likedTrackIds().size());
  }

  @Test
  void unlike_existingLike_removesFromSet() {
    catalog.addTrack("track-1");
    service.like(ACCOUNT, "track-1");
    service.unlike(ACCOUNT, "track-1");

    LikeSets sets = repo.likeSets(ACCOUNT);
    assertFalse(sets.likedTrackIds().contains("track-1"));
  }

  @Test
  void unlike_notLiked_isIdempotent() {
    // should not throw
    assertDoesNotThrow(() -> service.unlike(ACCOUNT, "track-x"));
  }
}
