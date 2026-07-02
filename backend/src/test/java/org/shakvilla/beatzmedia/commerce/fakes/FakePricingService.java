package org.shakvilla.beatzmedia.commerce.fakes;

import java.util.HashMap;
import java.util.Map;

import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricingService;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/** In-memory fake PricingService for unit tests. Seed with priceFor(kind, refId, PricedItem). */
public class FakePricingService implements PricingService {

  private final Map<String, PricedItem> priced = new HashMap<>();

  public void seed(CartItemKind kind, String refId, String title, long priceMinor) {
    priced.put(key(kind, refId),
        new PricedItem(title, "Subtitle", "img.jpg", Money.ofMinor(priceMinor, Currency.GHS)));
  }

  @Override
  public PricedItem priceFor(CartItemKind kind, String refId, Map<String, Object> metadata) {
    PricedItem item = priced.get(key(kind, refId));
    if (item == null) {
      throw new PriceUnavailableException(kind.wireValue(), refId);
    }
    return item;
  }

  private String key(CartItemKind kind, String refId) {
    return kind.wireValue() + ":" + refId;
  }
}
