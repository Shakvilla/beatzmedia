package org.shakvilla.beatzmedia.commerce.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.in.AddCartItemCommand;
import org.shakvilla.beatzmedia.commerce.application.port.in.CartView;
import org.shakvilla.beatzmedia.commerce.application.service.AddCartItemService;
import org.shakvilla.beatzmedia.commerce.application.service.GetCartService;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.fakes.FakeCartRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOwnershipReader;
import org.shakvilla.beatzmedia.commerce.fakes.FakePricingService;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Unit tests for GetCartService: totals math (subtotal/fee/total/count), empty cart. Commerce ADD
 * §11 / LLFR-COMMERCE-01.1.
 */
@Tag("unit")
class GetCartServiceTest {

  private static final AccountId ACCOUNT = new AccountId("acct-1");

  FakeCartRepository repo;
  FakePricingService pricing;
  FakeOwnershipReader ownership;
  FakePlatformSettingsProvider settings;
  GetCartService getCart;
  AddCartItemService addCartItem;

  @BeforeEach
  void setUp() {
    repo = new FakeCartRepository();
    pricing = new FakePricingService();
    ownership = new FakeOwnershipReader();
    settings = new FakePlatformSettingsProvider();
    getCart = new GetCartService(repo, settings);
    addCartItem = new AddCartItemService(repo, pricing, ownership, settings, FakeIds.sequential("cart"));
  }

  @Test
  void getCart_emptyCart_returnsZeros() {
    CartView view = getCart.getCart(ACCOUNT);

    assertTrue(view.items().isEmpty());
    assertEquals(BigDecimal.valueOf(0, 2).stripTrailingZeros(),
        view.subtotal().amount().stripTrailingZeros());
    assertEquals(0, view.fee().amount().compareTo(BigDecimal.ZERO));
    assertEquals(0, view.total().amount().compareTo(BigDecimal.ZERO));
    assertEquals(0, view.count());
  }

  @Test
  void getCart_withItems_computesSubtotalFeeTotalCount() {
    pricing.seed(CartItemKind.track, "t1", "Track 1", 500);
    addCartItem.add(ACCOUNT, new AddCartItemCommand("track", "t1", null, null));

    pricing.seed(CartItemKind.ticket, "tk1", "Ticket", 1000);
    addCartItem.add(ACCOUNT, new AddCartItemCommand("ticket", "tk1", 2, null));

    CartView view = getCart.getCart(ACCOUNT);

    // subtotal = 500*1 + 1000*2 = 2500 pesewas = 25.00 GHS
    assertEquals(0, view.subtotal().amount().compareTo(new BigDecimal("25.00")));
    // fee = serviceFeeMinor default (50 pesewas = 0.50 GHS) since items > 0
    assertEquals(0, view.fee().amount().compareTo(new BigDecimal("0.50")));
    // total = subtotal + fee = 25.50
    assertEquals(0, view.total().amount().compareTo(new BigDecimal("25.50")));
    // count = 1 + 2 = 3
    assertEquals(3, view.count());
    assertEquals("GHS", view.subtotal().currency());
  }

  @Test
  void getCart_feeIsZero_whenCartEmpty() {
    CartView view = getCart.getCart(ACCOUNT);
    assertEquals(0, view.fee().amount().compareTo(BigDecimal.ZERO));
  }
}
