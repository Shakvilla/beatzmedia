package org.shakvilla.beatzmedia.commerce.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.service.GetTrackDownloadPermissionService;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OwnershipGrant;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOwnershipRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * The read side of the download permission: commerce answers "may this account download this
 * track?" from the caller's own ownership GRANT.
 *
 * <p>{@link #theGrantIsTheAuthorityNotTheRelease()} is the behavioural guard on the design's most
 * fragile decision. This is the layer where the wrong implementation would compile — commerce owns
 * a {@code CatalogExpansionReader.isDownloadable(kind, refId)} port (added in Task 5 for the WRITE
 * path) that reads {@code release.downloadable}, and answering this read from that port instead of
 * from the grant would silently retract downloads from people who already paid.
 */
@Tag("unit")
class GetTrackDownloadPermissionServiceTest {

  private static final AccountId BUYER = new AccountId("acct-1");
  private static final AccountId STRANGER = new AccountId("acct-2");
  private static final String TRACK = "t1";
  private static final Instant GRANTED_AT = Instant.parse("2026-08-01T10:00:00Z");

  FakeOwnershipRepository ownership;
  GetTrackDownloadPermissionService service;

  @BeforeEach
  void setUp() {
    ownership = new FakeOwnershipRepository();
    service = new GetTrackDownloadPermissionService(ownership);
  }

  private OwnershipGrant grant(AccountId account, String trackId, boolean downloadable) {
    return ownership.save(
        OwnershipGrant.forTrack(
            "grant-" + account.value() + "-" + trackId,
            account,
            trackId,
            new OrderId("ord-1"),
            GRANTED_AT,
            downloadable));
  }

  @Test
  void aGrantThatPermitsDownloadingSaysYes() {
    grant(BUYER, TRACK, true);

    assertTrue(service.mayDownload(BUYER, TRACK));
  }

  @Test
  void aGrantThatForbidsDownloadingSaysNo() {
    grant(BUYER, TRACK, false);

    assertFalse(service.mayDownload(BUYER, TRACK));
  }

  @Test
  void anAccountWithNoGrantSaysNo() {
    grant(BUYER, TRACK, true);

    assertFalse(service.mayDownload(STRANGER, TRACK), "someone else's grant is not a permission");
  }

  @Test
  void aRevokedGrantSaysNo() {
    // INV-9: a refund revokes ownership, and the download permission goes with it.
    OwnershipGrant refunded = grant(BUYER, TRACK, true);
    refunded.revoke(Instant.parse("2026-08-02T10:00:00Z"));

    assertFalse(service.mayDownload(BUYER, TRACK));
  }

  @Test
  void theGrantIsTheAuthorityNotTheRelease() {
    // Two tracks of the SAME release, bought either side of the artist changing their mind. The
    // first purchase settled while downloads were allowed and its grant carries downloadable=true;
    // the second settled after the artist said no and carries false. `release.downloadable` is a
    // single value and is now false for both — so ANY implementation that answers from the release
    // must give both tracks the same answer, and cannot produce the split below.
    grant(BUYER, "t-bought-before-the-artist-said-no", true);
    grant(BUYER, "t-bought-after-the-artist-said-no", false);

    assertTrue(
        service.mayDownload(BUYER, "t-bought-before-the-artist-said-no"),
        "a permission captured at purchase must survive the artist changing their mind");
    assertFalse(
        service.mayDownload(BUYER, "t-bought-after-the-artist-said-no"),
        "a purchase made after the artist said no carries no download permission");
  }
}
