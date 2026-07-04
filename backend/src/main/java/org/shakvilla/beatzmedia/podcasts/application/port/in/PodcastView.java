package org.shakvilla.beatzmedia.podcasts.application.port.in;

/**
 * Read-model / DTO for a podcast show. Field names match the {@code Podcast} TypeScript type in
 * {@code Frontend/src/types/index.ts} and {@code API-CONTRACT.md} §8. ADD §6.
 */
public record PodcastView(
    String id,
    String title,
    String publisher,
    String image,
    String category,
    String description,
    Integer episodeCount,
    Integer popularity,
    MoneyView seasonPassPrice,
    Boolean supportsTips) {}
