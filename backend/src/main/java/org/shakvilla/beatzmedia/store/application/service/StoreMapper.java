package org.shakvilla.beatzmedia.store.application.service;

import org.shakvilla.beatzmedia.store.application.port.in.LicenseOptionView;
import org.shakvilla.beatzmedia.store.application.port.in.MerchVariantView;
import org.shakvilla.beatzmedia.store.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.store.application.port.in.StoreItemView;
import org.shakvilla.beatzmedia.store.domain.LicenseOption;
import org.shakvilla.beatzmedia.store.domain.MerchVariant;
import org.shakvilla.beatzmedia.store.domain.StoreItem;

/**
 * Maps store domain aggregates to their wire read-models. Type-specific fields are only populated
 * when non-empty/present on the domain aggregate (INV-STORE-A already guarantees they are absent
 * for the "wrong" type). Store ADD §6.
 */
final class StoreMapper {

  private StoreMapper() {}

  static StoreItemView toView(StoreItem item) {
    return new StoreItemView(
        item.id().value(),
        item.type().name(),
        item.title(),
        item.artistName(),
        item.artistId().orElse(null),
        item.image(),
        MoneyView.ofMinor(item.priceMinor(), item.currency().name()),
        item.genre().map(g -> g.wireValue()).orElse(null),
        item.badges().isEmpty() ? null : item.badges(),
        item.description().orElse(null),
        item.popularity().orElse(null),
        item.createdAt() == null ? null : item.createdAt().toString(),
        item.licenseOptions().isEmpty()
            ? null
            : item.licenseOptions().stream().map(o -> toView(o, item.currency().name())).toList(),
        item.variants().isEmpty() ? null : item.variants().stream().map(StoreMapper::toView).toList(),
        item.quality().orElse(null),
        item.dropsAt().map(Object::toString).orElse(null),
        item.stockRemaining().orElse(null));
  }

  private static LicenseOptionView toView(LicenseOption option, String currency) {
    return new LicenseOptionView(
        option.tier().name(),
        option.label(),
        MoneyView.ofMinor(option.priceMinor(), currency),
        option.features(),
        option.terms());
  }

  private static MerchVariantView toView(MerchVariant variant) {
    return new MerchVariantView(variant.label(), variant.options());
  }
}
