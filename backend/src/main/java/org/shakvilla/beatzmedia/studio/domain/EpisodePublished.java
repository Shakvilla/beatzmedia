package org.shakvilla.beatzmedia.studio.domain;

import java.time.Instant;

/**
 * Domain event fired after-commit when an episode transitions to {@code published} — either
 * publish-now ({@code CreateEpisode}/{@code UpdateEpisode}) or scheduled go-live (INV-7, the
 * {@code EpisodeGoLiveJob} scheduler). Fired exactly once per transition. Carries only ids + a
 * minimal snapshot (mirrors {@code events.domain.TicketTierSoldOut}). Studio ADD §2 / §9.
 */
public record EpisodePublished(String episodeId, String showId, String artistId, Instant occurredAt) {}
