package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/**
 * Read-model / DTO for a track. Field names match the {@code Track} TypeScript type in
 * {@code Frontend/src/types/index.ts} and {@code API-CONTRACT.md} §3. Money is {@link MoneyView}
 * (decimal cedis + currency). Duration is whole seconds. Catalog ADD §6 / INV-11.
 */
public record TrackView(
    String id,
    String title,
    String artistId,
    String artistName,
    String albumId,
    String albumTitle,
    /** Duration in whole seconds. */
    int duration,
    String image,
    /** Wire value: "owned" | "free" | "for-sale". */
    String ownership,
    /** Present when ownership is "for-sale". */
    MoneyView price,
    Long plays,
    String audioUrl,
    List<TrackCreditView> credits,
    String quality,
    Integer year) {}
