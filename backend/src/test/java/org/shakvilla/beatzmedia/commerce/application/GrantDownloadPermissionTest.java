package org.shakvilla.beatzmedia.commerce.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.commerce.application.service.GrantOwnershipService;
import org.shakvilla.beatzmedia.commerce.application.service.SettlementSourceRegistry;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OrderLine;
import org.shakvilla.beatzmedia.commerce.domain.OwnershipGranted;
import org.shakvilla.beatzmedia.commerce.domain.SaleRecorded;
import org.shakvilla.beatzmedia.commerce.fakes.FakeCartRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeCatalogExpansionReader;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOrderRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOwnershipRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeSaleLedgerPoster;
import org.shakvilla.beatzmedia.commerce.fakes.RecordingEvent;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * The grant records the permission as it stood at purchase.
 *
 * <p>{@link #anExistingGrantKeepsItsPermissionWhenTheArtistLaterRefuses()} is the executable
 * statement of the grandfathering rule — the one decision a later "simplification" is most likely
 * to undo by reading the release instead of the grant.
 */
@Tag("unit")
class GrantDownloadPermissionTest {

  private static final AccountId BUYER = new AccountId("buyer-1");
  private static final String CREATOR = "artist-1";
  private static final String RELEASE = "r1";
  private static final String TRACK = "t1";
  private static final Instant NOW = Instant.parse("2026-07-03T10:00:00Z");

  FakeOrderRepository orders;
  FakeOwnershipRepository ownershipRepository;
  FakeCatalogExpansionReader expansion;
  GrantOwnershipService grantOwnershipService;

  @BeforeEach
  void setUp() {
    orders = new FakeOrderRepository();
    ownershipRepository = new FakeOwnershipRepository();
    expansion = new FakeCatalogExpansionReader();
    grantOwnershipService =
        new GrantOwnershipService(
            orders,
            ownershipRepository,
            expansion,
            new SettlementSourceRegistry(List.of()),
            new FakeSaleLedgerPoster(),
            new FakeCartRepository(),
            new FakeAuditWriter(),
            new RecordingEvent<OwnershipGranted>(),
            new RecordingEvent<SaleRecorded>(),
            FakeIds.sequential("grant"),
            FakeClock.fixed());
  }

  /** Seed release {@code RELEASE}'s download choice and its single for-sale track {@code TRACK}. */
  private void seedRelease(String releaseId, boolean downloadable) {
    expansion.seedTrackForRelease(TRACK, releaseId, CREATOR);
    expansion.seedRelease(releaseId, downloadable);
  }

  private void pendingOrder(String reference) {
    Order order =
        Order.create(
            new OrderId("o-" + reference),
            BUYER,
            reference,
            List.of(
                new OrderLine(
                    "l-" + TRACK,
                    CartItemKind.track,
                    TRACK,
                    "Track",
                    "Artist",
                    "img.jpg",
                    Money.ofMinor(1000, Currency.GHS),
                    1)),
            Money.ofMinor(1000, Currency.GHS),
            Currency.GHS,
            NOW);
    order.bindIdempotency("key-" + reference, "hash-" + reference);
    order.attachPaymentIntent("intent-" + reference);
    orders.save(order);
  }

  @Test
  void aGrantFromADownloadableReleaseIsDownloadable() {
    seedRelease(RELEASE, true);
    pendingOrder("BZ-2026-00001");

    grantOwnershipService.grantForSettledOrder("BZ-2026-00001", "pi_1", "mtn");

    assertTrue(ownershipRepository.findByTrack(TRACK).isDownloadable());
  }

  @Test
  void aGrantFromANonDownloadableReleaseIsNot() {
    seedRelease(RELEASE, false);
    pendingOrder("BZ-2026-00002");

    grantOwnershipService.grantForSettledOrder("BZ-2026-00002", "pi_2", "mtn");

    assertFalse(ownershipRepository.findByTrack(TRACK).isDownloadable());
  }

  @Test
  void anExistingGrantKeepsItsPermissionWhenTheArtistLaterRefuses() {
    seedRelease(RELEASE, true);
    pendingOrder("BZ-2026-00003");
    grantOwnershipService.grantForSettledOrder("BZ-2026-00003", "pi_3", "mtn");

    seedRelease(RELEASE, false); // the artist changes their mind

    assertTrue(
        ownershipRepository.findByTrack(TRACK).isDownloadable(),
        "a permission captured at purchase must survive the artist changing it afterwards");
  }
}
