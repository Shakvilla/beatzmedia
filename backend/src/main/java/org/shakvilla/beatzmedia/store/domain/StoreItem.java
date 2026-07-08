package org.shakvilla.beatzmedia.store.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.Currency;

/**
 * A purchasable product in the Music Store (aggregate root), owning its type-specific children
 * ({@link LicenseOption} list, {@link MerchVariant} list). Framework-free. Store ADD §3.
 *
 * <p><b>INV-STORE-A (type/child consistency), enforced here at construction:</b> {@code
 * licenseOptions} is non-empty iff {@code type = BEAT_LICENSE}; {@code variants} present only for
 * {@code MERCH}; {@code quality} present only for {@code TRACK}/{@code ALBUM}; {@code dropsAt}
 * present only for {@code EXCLUSIVE}; {@code stockRemaining} present only for {@code EXCLUSIVE} /
 * {@code MERCH} (scarcity is reused across both).
 *
 * <p><b>INV-STORE-B ("from" price), enforced here for {@code BEAT_LICENSE}:</b> the base {@code
 * priceMinor} equals the lowest license-tier price; the {@code LEASE} tier (if present) is the
 * cheapest, the {@code EXCLUSIVE} tier (if present) is the dearest.
 *
 * <p><b>INV-STORE-C (stock floor), enforced here:</b> {@code stockRemaining}, when present, is
 * never negative. The persistence layer additionally floor-guards decrements (never below zero).
 */
public final class StoreItem {

  private final StoreItemId id;
  private final StoreItemType type;
  private final String title;
  private final String artistName;
  private final String artistId;
  private final String image;
  private final long priceMinor;
  private final Currency currency;
  private final Genre genre;
  private final List<String> badges;
  private final String description;
  private final Integer popularity;
  private final Instant createdAt;
  private final List<LicenseOption> licenseOptions;
  private final List<MerchVariant> variants;
  private final String quality;
  private final Instant dropsAt;
  private final Integer stockRemaining;

  public StoreItem(
      StoreItemId id,
      StoreItemType type,
      String title,
      String artistName,
      String artistId,
      String image,
      long priceMinor,
      Currency currency,
      Genre genre,
      List<String> badges,
      String description,
      Integer popularity,
      Instant createdAt,
      List<LicenseOption> licenseOptions,
      List<MerchVariant> variants,
      String quality,
      Instant dropsAt,
      Integer stockRemaining) {
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    if (artistName == null || artistName.isBlank()) {
      throw new IllegalArgumentException("artistName must not be blank");
    }
    if (image == null || image.isBlank()) {
      throw new IllegalArgumentException("image must not be blank");
    }
    if (priceMinor < 0) {
      throw new IllegalArgumentException("priceMinor must not be negative");
    }
    if (currency == null) {
      throw new IllegalArgumentException("currency must not be null");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
    if (stockRemaining != null && stockRemaining < 0) {
      throw new IllegalArgumentException("stockRemaining must not be negative (INV-STORE-C)");
    }

    List<LicenseOption> safeLicenseOptions = licenseOptions == null ? List.of() : List.copyOf(licenseOptions);
    List<MerchVariant> safeVariants = variants == null ? List.of() : List.copyOf(variants);

    // INV-STORE-A: licenseOptions non-empty iff type=BEAT_LICENSE.
    boolean hasLicenseOptions = !safeLicenseOptions.isEmpty();
    boolean isBeatLicense = type == StoreItemType.BEAT_LICENSE;
    if (hasLicenseOptions != isBeatLicense) {
      throw new IllegalArgumentException(
          "licenseOptions must be present iff type=BEAT_LICENSE (INV-STORE-A)");
    }
    // INV-STORE-A: variants present only for MERCH.
    if (!safeVariants.isEmpty() && type != StoreItemType.MERCH) {
      throw new IllegalArgumentException("variants are only allowed for type=MERCH (INV-STORE-A)");
    }
    // INV-STORE-A: quality present only for TRACK/ALBUM.
    if (quality != null && type != StoreItemType.TRACK && type != StoreItemType.ALBUM) {
      throw new IllegalArgumentException(
          "quality is only allowed for type=TRACK or type=ALBUM (INV-STORE-A)");
    }
    // INV-STORE-A: dropsAt present only for EXCLUSIVE.
    if (dropsAt != null && type != StoreItemType.EXCLUSIVE) {
      throw new IllegalArgumentException("dropsAt is only allowed for type=EXCLUSIVE (INV-STORE-A)");
    }
    // INV-STORE-A: stockRemaining present only for EXCLUSIVE/MERCH.
    if (stockRemaining != null && type != StoreItemType.EXCLUSIVE && type != StoreItemType.MERCH) {
      throw new IllegalArgumentException(
          "stockRemaining is only allowed for type=EXCLUSIVE or type=MERCH (INV-STORE-A)");
    }

    // INV-STORE-B: for BEAT_LICENSE, base price = lowest tier price; LEASE cheapest, EXCLUSIVE dearest.
    if (isBeatLicense) {
      long minPrice = safeLicenseOptions.stream().mapToLong(LicenseOption::priceMinor).min().orElseThrow();
      long maxPrice = safeLicenseOptions.stream().mapToLong(LicenseOption::priceMinor).max().orElseThrow();
      if (priceMinor != minPrice) {
        throw new IllegalArgumentException(
            "BEAT_LICENSE base priceMinor must equal the lowest license tier price (INV-STORE-B)");
      }
      Optional<Long> leasePrice = tierPrice(safeLicenseOptions, LicenseTier.LEASE);
      if (leasePrice.isPresent() && leasePrice.get() != minPrice) {
        throw new IllegalArgumentException("LEASE tier must be the cheapest license tier (INV-STORE-B)");
      }
      Optional<Long> exclusivePrice = tierPrice(safeLicenseOptions, LicenseTier.EXCLUSIVE);
      if (exclusivePrice.isPresent() && exclusivePrice.get() != maxPrice) {
        throw new IllegalArgumentException("EXCLUSIVE tier must be the dearest license tier (INV-STORE-B)");
      }
    }

    this.id = id;
    this.type = type;
    this.title = title;
    this.artistName = artistName;
    this.artistId = artistId;
    this.image = image;
    this.priceMinor = priceMinor;
    this.currency = currency;
    this.genre = genre;
    this.badges = badges == null ? List.of() : List.copyOf(badges);
    this.description = description;
    this.popularity = popularity;
    this.createdAt = createdAt;
    this.licenseOptions = safeLicenseOptions;
    this.variants = safeVariants;
    this.quality = quality;
    this.dropsAt = dropsAt;
    this.stockRemaining = stockRemaining;
  }

  private static Optional<Long> tierPrice(List<LicenseOption> options, LicenseTier tier) {
    return options.stream().filter(o -> o.tier() == tier).map(LicenseOption::priceMinor).findFirst();
  }

  public StoreItemId id() {
    return id;
  }

  public StoreItemType type() {
    return type;
  }

  public String title() {
    return title;
  }

  public String artistName() {
    return artistName;
  }

  public Optional<String> artistId() {
    return Optional.ofNullable(artistId);
  }

  public String image() {
    return image;
  }

  public long priceMinor() {
    return priceMinor;
  }

  public Currency currency() {
    return currency;
  }

  public Optional<Genre> genre() {
    return Optional.ofNullable(genre);
  }

  public List<String> badges() {
    return badges;
  }

  public Optional<String> description() {
    return Optional.ofNullable(description);
  }

  public Optional<Integer> popularity() {
    return Optional.ofNullable(popularity);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public List<LicenseOption> licenseOptions() {
    return licenseOptions;
  }

  public List<MerchVariant> variants() {
    return variants;
  }

  public Optional<String> quality() {
    return Optional.ofNullable(quality);
  }

  public Optional<Instant> dropsAt() {
    return Optional.ofNullable(dropsAt);
  }

  public Optional<Integer> stockRemaining() {
    return Optional.ofNullable(stockRemaining);
  }
}
