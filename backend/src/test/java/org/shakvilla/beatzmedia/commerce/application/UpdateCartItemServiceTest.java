package org.shakvilla.beatzmedia.commerce.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.in.AddCartItemCommand;
import org.shakvilla.beatzmedia.commerce.application.port.in.CartView;
import org.shakvilla.beatzmedia.commerce.application.service.AddCartItemService;
import org.shakvilla.beatzmedia.commerce.application.service.UpdateCartItemService;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.CartLineNotFoundException;
import org.shakvilla.beatzmedia.commerce.domain.NotStackableException;
import org.shakvilla.beatzmedia.commerce.fakes.FakeCartRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOwnershipReader;
import org.shakvilla.beatzmedia.commerce.fakes.FakePricingService;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/** Unit tests for UpdateCartItemService. Commerce ADD §11 / LLFR-COMMERCE-01.3. */
@Tag("unit")
class UpdateCartItemServiceTest {

  private static final AccountId ACCOUNT = new AccountId("acct-1");

  FakeCartRepository repo;
  FakePricingService pricing;
  FakeOwnershipReader ownership;
  FakePlatformSettingsProvider settings;
  AddCartItemService addService;
  UpdateCartItemService updateService;

  @BeforeEach
  void setUp() {
    repo = new FakeCartRepository();
    pricing = new FakePricingService();
    ownership = new FakeOwnershipReader();
    settings = new FakePlatformSettingsProvider();
    addService = new AddCartItemService(repo, pricing, ownership, settings, FakeIds.sequential("cart"));
    updateService = new UpdateCartItemService(repo, settings);
  }

  @Test
  void updateQuantity_stackableLine_updatesQty() {
    pricing.seed(CartItemKind.ticket, "tk1", "Ticket", 1000);
    addService.add(ACCOUNT, new AddCartItemCommand("ticket", "tk1", null, null));

    CartView view = updateService.updateQuantity(ACCOUNT, "ticket:tk1", 7);
    assertEquals(7, view.items().get(0).quantity());
  }

  @Test
  void updateQuantity_clampsAt99() {
    pricing.seed(CartItemKind.ticket, "tk1", "Ticket", 1000);
    addService.add(ACCOUNT, new AddCartItemCommand("ticket", "tk1", null, null));

    CartView view = updateService.updateQuantity(ACCOUNT, "ticket:tk1", 500);
    assertEquals(99, view.items().get(0).quantity());
  }

  @Test
  void updateQuantity_nonStackableLine_throwsNotStackable() {
    pricing.seed(CartItemKind.track, "t1", "Track", 500);
    addService.add(ACCOUNT, new AddCartItemCommand("track", "t1", null, null));

    assertThrows(NotStackableException.class,
        () -> updateService.updateQuantity(ACCOUNT, "track:t1", 3));
  }

  @Test
  void updateQuantity_missingLine_throwsCartLineNotFound() {
    pricing.seed(CartItemKind.ticket, "tk1", "Ticket", 1000);
    addService.add(ACCOUNT, new AddCartItemCommand("ticket", "tk1", null, null));

    assertThrows(CartLineNotFoundException.class,
        () -> updateService.updateQuantity(ACCOUNT, "ticket:nonexistent", 3));
  }

  @Test
  void updateQuantity_noCartAtAll_throwsCartLineNotFound() {
    assertThrows(CartLineNotFoundException.class,
        () -> updateService.updateQuantity(ACCOUNT, "ticket:tk1", 3));
  }

  @Test
  void updateQuantity_belowMin_clampsToOne() {
    pricing.seed(CartItemKind.ticket, "tk1", "Ticket", 1000);
    addService.add(ACCOUNT, new AddCartItemCommand("ticket", "tk1", null, null));

    CartView view = updateService.updateQuantity(ACCOUNT, "ticket:tk1", 0);
    assertEquals(1, view.items().get(0).quantity());
  }

  @Test
  void updateQuantity_aboveMax_clampsTo99() {
    pricing.seed(CartItemKind.ticket, "tk1", "Ticket", 1000);
    addService.add(ACCOUNT, new AddCartItemCommand("ticket", "tk1", null, null));

    CartView view = updateService.updateQuantity(ACCOUNT, "ticket:tk1", 100);
    assertEquals(99, view.items().get(0).quantity());
  }
}
