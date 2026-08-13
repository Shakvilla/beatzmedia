package org.shakvilla.beatzmedia.library.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.CollectionView;
import org.shakvilla.beatzmedia.library.application.service.GetCollectionService;
import org.shakvilla.beatzmedia.library.fakes.FakeCollectionRepository;
import org.shakvilla.beatzmedia.library.fakes.FakeDownloadPermissionReader;
import org.shakvilla.beatzmedia.library.fakes.FakeLibraryOwnershipReader;

/**
 * GET /v1/me/collection. Library ADD §4.1 / LLFR-LIBRARY-01.1.
 *
 * <p>{@link #theLibraryReportsTheGrantsPermissionNotTheReleasesCurrentSetting()} is the behavioural
 * guard for Task 9b: the collection view's per-track {@code downloadable} flag must be sourced from
 * the buyer's own ownership grant (via {@link org.shakvilla.beatzmedia.library.application.port.out.DownloadPermissionReader}),
 * never from the release's current setting — because those two can disagree the moment an artist
 * changes their mind after a sale, and the grant is what actually governs {@code GET
 * /v1/tracks/{id}/download}. A buyer must keep the visible ability to exercise a right they paid for.
 */
@Tag("unit")
class GetCollectionServiceTest {

  private static final AccountId ACCOUNT = new AccountId("acct-1");

  FakeCollectionRepository repo;
  FakeLibraryOwnershipReader ownershipReader;
  FakeDownloadPermissionReader downloadPermissionReader;
  GetCollectionService service;

  @BeforeEach
  void setUp() {
    repo = new FakeCollectionRepository();
    ownershipReader = new FakeLibraryOwnershipReader();
    downloadPermissionReader = new FakeDownloadPermissionReader();
    service = new GetCollectionService(repo, ownershipReader, downloadPermissionReader);
  }

  @Test
  void theLibraryReportsTheGrantsPermissionNotTheReleasesCurrentSetting() {
    // Bought while downloads were allowed: the grant captured true, and the fake permission
    // reader models exactly that (it never consults a release at all — the release's current
    // setting, forbidding or not, is irrelevant to what the grant already captured).
    ownershipReader.grant(ACCOUNT, "t1");
    downloadPermissionReader.permit("t1");

    CollectionView view = service.get(ACCOUNT);

    assertTrue(
        view.downloadableTracks().contains("t1"),
        "a buyer must keep the download they paid for, and must be able to SEE that they have it");
  }

  @Test
  void aTrackOwnedWithoutDownloadPermissionIsNotReportedAsDownloadable() {
    ownershipReader.grant(ACCOUNT, "t2");
    // downloadPermissionReader.permit(...) deliberately not called for t2.

    CollectionView view = service.get(ACCOUNT);

    assertTrue(view.ownedTracks().contains("t2"));
    assertFalse(view.downloadableTracks().contains("t2"));
  }

  @Test
  void theDownloadPermissionQueryIsBatchedOncePerCollectionLoad() {
    // A library with 200 owned tracks must not fire 200 permission queries.
    for (int i = 0; i < 200; i++) {
      ownershipReader.grant(ACCOUNT, "t" + i);
    }
    downloadPermissionReader.permit("t0");

    service.get(ACCOUNT);

    assertEquals(1, downloadPermissionReader.callCount());
  }
}
