package org.shakvilla.beatzmedia.store.adapter.out.pricing;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.out.ModulePriceSource;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.store.application.port.out.StoreRepository;
import org.shakvilla.beatzmedia.store.domain.LicenseOption;
import org.shakvilla.beatzmedia.store.domain.LicenseTier;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreItemType;

/**
 * Contributes authoritative {@code store} pricing to commerce via the {@link ModulePriceSource} SPI
 * (WU-COM-4). Store owns the data; commerce never reads the store tables.
 *
 * <p>The {@code refId} is the {@link StoreItemId}, optionally suffixed with a display note
 * ({@code item-1:M} for a merch size / license label) — the note is stripped; the authoritative
 * license-tier selector is {@code metadata.licenseTier}, never the note. For a {@code BEAT_LICENSE}
 * item with a {@code licenseTier}, the price is that tier's {@code license_option.price_minor}; an
 * unknown tier is rejected 404 rather than falling back to the base price. Otherwise the item's base
 * {@code price_minor} stands (for {@code BEAT_LICENSE} that is the cheapest tier, INV-STORE-B).
 */
@ApplicationScoped
public class StorePriceSource implements ModulePriceSource {

  private final StoreRepository repository;

  @Inject
  public StorePriceSource(StoreRepository repository) {
    this.repository = repository;
  }

  @Override
  public String entityType() {
    return "store";
  }

  @Override
  public PricedItem price(String refId, Map<String, Object> metadata) {
    StoreItem item =
        repository
            .findById(new StoreItemId(stripNote(refId)))
            .orElseThrow(() -> new PriceUnavailableException("store", refId));
    long priceMinor = resolvePriceMinor(item, metadata, refId);
    return new PricedItem(
        item.title(), item.artistName(), item.image(),
        Money.ofMinor(priceMinor, item.currency()));
  }

  /** Strip a trailing display note (size/tier label) — the id is everything before the first colon. */
  private static String stripNote(String refId) {
    int colon = refId == null ? -1 : refId.indexOf(':');
    return colon < 0 ? refId : refId.substring(0, colon);
  }

  private long resolvePriceMinor(StoreItem item, Map<String, Object> metadata, String refId) {
    Object tierObj = metadata == null ? null : metadata.get("licenseTier");
    if (item.type() == StoreItemType.BEAT_LICENSE
        && tierObj instanceof String tierStr
        && !tierStr.isBlank()) {
      LicenseTier tier;
      try {
        tier = LicenseTier.fromWireValue(tierStr);
      } catch (IllegalArgumentException e) {
        throw new PriceUnavailableException("store", refId);
      }
      return item.licenseOptions().stream()
          .filter(option -> option.tier() == tier)
          .mapToLong(LicenseOption::priceMinor)
          .findFirst()
          .orElseThrow(() -> new PriceUnavailableException("store", refId));
    }
    return item.priceMinor();
  }
}
