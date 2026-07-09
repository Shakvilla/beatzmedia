package org.shakvilla.beatzmedia.studio.application.port.in;

import java.math.BigDecimal;

/**
 * {@code StudioEpisodeDto} — Studio ADD §6: {@code id}, {@code showId}, {@code showTitle}, {@code
 * title}, {@code duration} (seconds), {@code status}, {@code premium}, {@code price} (decimal
 * cedis; {@code 0} when free — a bare number, NOT the {@code {amount,currency}} money envelope used
 * on money-path endpoints, matching {@code Frontend/src/lib/studio-data.ts} {@code
 * StudioEpisode.price: number}), {@code publishedAt} (ISO-8601; the actual publish instant once
 * {@code published}, the anticipated go-live instant while {@code scheduled}, {@code null} while
 * {@code draft} — mirrors the {@code studio-data.ts} mock convention), {@code plays}.
 */
public record EpisodeView(
    String id,
    String showId,
    String showTitle,
    String title,
    int duration,
    String status,
    boolean premium,
    BigDecimal price,
    String publishedAt,
    long plays) {}
