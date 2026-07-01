package org.shakvilla.beatzmedia.commerce.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.in.AddCartItemCommand;
import org.shakvilla.beatzmedia.commerce.application.port.in.CartView;
import org.shakvilla.beatzmedia.commerce.application.service.AddCartItemService;
import org.shakvilla.beatzmedia.commerce.application.service.RemoveCartItemService;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.fakes.FakeCartRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOwnershipReader;
import org.shakvilla.beatzmedia.commerce.fakes.FakePricingService;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/** Unit tests for RemoveCartItemService: idempotent remove-missing. Commerce ADD §11 / LLFR-COMMERCE-01.3. */
@Tag("unit")
class RemoveCartItemServiceTest {

  private static final AccountId ACCOUNT = new AccountId("acct-1");

  FakeCartRepository repo;
  FakePricingService pricing;
  FakeOwnershipReader ownership;
  FakePlatformSettingsProvider settings;
  AddCartItemService addService;
  RemoveCartItemService removeService;

  @BeforeEach
  void setUp() {
    repo = new FakeCartRepository();
    pricing = new FakePricingService();
    ownership = new FakeOwnershipReader();
    settings = new FakePlatformSettingsProvider();
    addService = new AddCartItemService(repo, pricing, ownership, settings, FakeIds.sequential("cart"));
    removeService = new RemoveCartItemService(repo, settings);
  }

  @Test
  void remove_existingLine_removesFromCart() {
    pricing.seed(CartItemKind.track, "t1", "Track", 500);
    addService.add(ACCOUNT, new AddCartItemCommand("track", "t1", null, null));

    CartView view = removeService.remove(ACCOUNT, "track:t1");
    assertTrue(view.items().isEmpty());
    assertEquals(0, view.count());
  }

  @Test
  void remove_missingLine_isIdempotent_noThrow() {
    pricing.seed(CartItemKind.track, "t1", "Track", 500);
    addService.add(ACCOUNT, new AddCartItemCommand("track", "t1", null, null));

    assertDoesNotThrow(() -> removeService.remove(ACCOUNT, "track:nonexistent"));
  }

  @Test
  void remove_noCartAtAll_returnsEmptyCartView_noThrow() {
    CartView view = assertDoesNotThrow(() -> removeService.remove(ACCOUNT, "track:t1"));
    assertTrue(view.items().isEmpty());
    assertEquals(0, view.count());
  }
}
