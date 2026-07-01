package org.shakvilla.beatzmedia.commerce.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.in.AddCartItemCommand;
import org.shakvilla.beatzmedia.commerce.application.port.in.CartView;
import org.shakvilla.beatzmedia.commerce.application.service.AddCartItemService;
import org.shakvilla.beatzmedia.commerce.domain.AlreadyOwnedException;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.InvalidCartItemKindException;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.commerce.fakes.FakeCartRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOwnershipReader;
import org.shakvilla.beatzmedia.commerce.fakes.FakePricingService;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Unit tests for AddCartItemService: already-owned rejection, price resolution, stackability
 * end-to-end. Commerce ADD §11 / LLFR-COMMERCE-01.2.
 */
@Tag("unit")
class AddCartItemServiceTest {

  private static final AccountId ACCOUNT = new AccountId("acct-1");

  FakeCartRepository repo;
  FakePricingService pricing;
  FakeOwnershipReader ownership;
  FakePlatformSettingsProvider settings;
  AddCartItemService service;

  @BeforeEach
  void setUp() {
    repo = new FakeCartRepository();
    pricing = new FakePricingService();
    ownership = new FakeOwnershipReader();
    settings = new FakePlatformSettingsProvider();
    service = new AddCartItemService(repo, pricing, ownership, settings, FakeIds.sequential("cart"));
  }

  @Test
  void add_alreadyOwnedTrack_throwsAlreadyOwned() {
    pricing.seed(CartItemKind.track, "t1", "Track 1", 500);
    ownership.markOwned(ACCOUNT, CartItemKind.track, "t1");

    assertThrows(AlreadyOwnedException.class,
        () -> service.add(ACCOUNT, new AddCartItemCommand("track", "t1", null, null)));
  }

  @Test
  void add_unresolvablePrice_throwsPriceUnavailable() {
    assertThrows(PriceUnavailableException.class,
        () -> service.add(ACCOUNT, new AddCartItemCommand("track", "unknown", null, null)));
  }

  @Test
  void add_unknownKind_throwsInvalidCartItemKind() {
    assertThrows(InvalidCartItemKindException.class,
        () -> service.add(ACCOUNT, new AddCartItemCommand("bogus", "x", null, null)));
  }

  @Test
  void add_blankRefId_throwsValidation() {
    assertThrows(ValidationException.class,
        () -> service.add(ACCOUNT, new AddCartItemCommand("track", " ", null, null)));
  }

  @Test
  void add_qtyOutOfRange_throwsValidation() {
    pricing.seed(CartItemKind.ticket, "tk1", "Ticket", 1000);
    assertThrows(ValidationException.class,
        () -> service.add(ACCOUNT, new AddCartItemCommand("ticket", "tk1", 0, null)));
    assertThrows(ValidationException.class,
        () -> service.add(ACCOUNT, new AddCartItemCommand("ticket", "tk1", 100, null)));
  }

  @Test
  void add_trackTwice_cartUnchanged_qtyStaysOne() {
    pricing.seed(CartItemKind.track, "t1", "Track 1", 500);

    service.add(ACCOUNT, new AddCartItemCommand("track", "t1", null, null));
    CartView second = service.add(ACCOUNT, new AddCartItemCommand("track", "t1", null, null));

    assertEquals(1, second.items().size());
    assertEquals(1, second.items().get(0).quantity());
  }

  @Test
  void add_ticketTwice_qtyIsTwo() {
    pricing.seed(CartItemKind.ticket, "tk1", "VIP Ticket", 10000);

    service.add(ACCOUNT, new AddCartItemCommand("ticket", "tk1", null, null));
    CartView second = service.add(ACCOUNT, new AddCartItemCommand("ticket", "tk1", null, null));

    assertEquals(1, second.items().size());
    assertEquals(2, second.items().get(0).quantity());
  }

  @Test
  void add_storeItemWithMetadata_carriesMetadataThrough() {
    pricing.seed(CartItemKind.store, "tshirt-1", "T-Shirt", 3000);
    CartView view = service.add(ACCOUNT,
        new AddCartItemCommand("store", "tshirt-1", null, Map.of("merchVariants", "L")));

    assertEquals("L", view.items().get(0).metadata().get("merchVariants"));
  }

  @Test
  void add_albumRest_isNonStackable() {
    pricing.seed(CartItemKind.album_rest, "iron-boy", "Iron Boy (rest)", 2000);
    service.add(ACCOUNT, new AddCartItemCommand("album-rest", "iron-boy", null, null));
    CartView second = service.add(ACCOUNT, new AddCartItemCommand("album-rest", "iron-boy", null, null));

    assertEquals(1, second.items().get(0).quantity());
    assertEquals("album-rest", second.items().get(0).kind());
  }
}
