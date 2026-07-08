package org.shakvilla.beatzmedia.store.adapter.out.persistence;

import java.util.Comparator;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.store.domain.Genre;
import org.shakvilla.beatzmedia.store.domain.LicenseOption;
import org.shakvilla.beatzmedia.store.domain.LicenseTier;
import org.shakvilla.beatzmedia.store.domain.MerchVariant;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreItemType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps {@code store_item}/{@code license_option}/{@code merch_variant} JPA entities to domain
 * objects. Domain carries no ORM annotations (ArchUnit-enforced); this is the only place the
 * mapping happens. Read-only — this WU has no admin write path (the dev seed inserts rows
 * directly via SQL). Store ADD §5.2 / §7.
 */
@ApplicationScoped
public class StoreEntityMapper {

  private final ObjectMapper objectMapper;

  @Inject
  public StoreEntityMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  StoreItem toDomain(
      StoreItemEntity e, List<LicenseOptionEntity> licenseEntities, List<MerchVariantEntity> variantEntities) {
    List<LicenseOption> licenseOptions =
        licenseEntities.stream()
            .sorted(Comparator.comparing(l -> l.sortOrder))
            .map(this::toDomain)
            .toList();
    List<MerchVariant> variants =
        variantEntities.stream()
            .sorted(Comparator.comparing(v -> v.sortOrder))
            .map(this::toDomain)
            .toList();
    return new StoreItem(
        new StoreItemId(e.id),
        StoreItemType.fromWireValue(e.type),
        e.title,
        e.artistName,
        e.artistId,
        e.image,
        e.priceMinor,
        Currency.valueOf(e.currency),
        e.genre == null ? null : Genre.fromWireValue(e.genre),
        readStringList(e.badgesJson),
        e.description,
        e.popularity,
        e.createdAt,
        licenseOptions,
        variants,
        e.quality,
        e.dropsAt,
        e.stockRemaining);
  }

  private LicenseOption toDomain(LicenseOptionEntity e) {
    return new LicenseOption(
        LicenseTier.fromWireValue(e.tier), e.label, e.priceMinor, readStringList(e.featuresJson), e.terms);
  }

  private MerchVariant toDomain(MerchVariantEntity e) {
    return new MerchVariant(e.label, readStringList(e.optionsJson));
  }

  private List<String> readStringList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }
}
