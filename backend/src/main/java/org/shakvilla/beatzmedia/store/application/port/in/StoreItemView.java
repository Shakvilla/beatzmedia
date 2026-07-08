package org.shakvilla.beatzmedia.store.application.port.in;

import java.util.List;

/**
 * Read-model / DTO for a store product. Field names match the {@code StoreItem} TypeScript type
 * in {@code Frontend/src/types/index.ts} and {@code API-CONTRACT.md} §7 (used for both the list
 * feed and the detail view). Type-specific fields ({@code licenseOptions}, {@code variants},
 * {@code quality}, {@code dropsAt}, {@code stockRemaining}) are populated only for their owning
 * {@code type} (INV-STORE-A) — {@code null}/absent otherwise. Store ADD §6.
 */
public record StoreItemView(
    String id,
    String type,
    String title,
    String artistName,
    String artistId,
    String image,
    MoneyView price,
    String genre,
    List<String> badges,
    String description,
    Integer popularity,
    String createdAt,
    List<LicenseOptionView> licenseOptions,
    List<MerchVariantView> variants,
    String quality,
    String dropsAt,
    Integer stockRemaining) {}
