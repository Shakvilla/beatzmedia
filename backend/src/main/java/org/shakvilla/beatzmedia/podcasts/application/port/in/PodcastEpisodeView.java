package org.shakvilla.beatzmedia.podcasts.application.port.in;

/**
 * Read-model / DTO for a podcast episode, decorated with per-caller ownership/early-access state.
 * Field names match the {@code PodcastEpisode} TypeScript type in
 * {@code Frontend/src/types/index.ts} and {@code API-CONTRACT.md} §8. Duration is whole seconds;
 * timestamps are ISO-8601. ADD §6.
 */
public record PodcastEpisodeView(
    String id,
    String podcastId,
    String title,
    String showTitle,
    String image,
    int duration,
    String publishedAt,
    String description,
    Integer episodeNumber,
    Boolean isPremium,
    MoneyView price,
    Boolean isOwned,
    Boolean isEarlyAccess,
    String publicAt) {}
