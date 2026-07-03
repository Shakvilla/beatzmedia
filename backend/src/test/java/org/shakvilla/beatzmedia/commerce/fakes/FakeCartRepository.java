package org.shakvilla.beatzmedia.commerce.fakes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.commerce.application.port.out.CartRepository;
import org.shakvilla.beatzmedia.commerce.domain.Cart;
import org.shakvilla.beatzmedia.commerce.domain.CartId;
import org.shakvilla.beatzmedia.commerce.domain.CartItem;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** In-memory fake CartRepository for unit tests. */
public class FakeCartRepository implements CartRepository {

  private final Map<String, Cart> byAccount = new HashMap<>();

  @Override
  public Optional<Cart> findByAccount(AccountId account) {
    Cart cart = byAccount.get(account.value());
    if (cart == null) {
      return Optional.empty();
    }
    // Return a deep-ish copy so callers mutate a fresh in-memory snapshot like a real repo would.
    return Optional.of(new Cart(cart.getId(), cart.getAccountId(),
        cart.getItems().stream().map(this::copyItem).toList()));
  }

  @Override
  public Cart save(Cart cart) {
    CartId id = cart.getId() != null ? cart.getId() : new CartId("fake-cart-" + cart.getAccountId().value());
    Cart toStore = new Cart(id, cart.getAccountId(), cart.getItems());
    byAccount.put(cart.getAccountId().value(), toStore);
    return toStore;
  }

  @Override
  public void deleteByAccount(AccountId account) {
    byAccount.remove(account.value());
  }

  /** Test helper: does this account currently have a (non-deleted) cart? */
  public boolean hasCart(AccountId account) {
    return byAccount.containsKey(account.value());
  }

  private CartItem copyItem(CartItem item) {
    return new CartItem(
        item.getLineId(), item.getKind(), item.getRefId(), item.getTitle(), item.getSubtitle(),
        item.getImage(), item.getUnitPrice(), item.getQty(), item.isStackable(),
        item.getMetadata());
  }
}
