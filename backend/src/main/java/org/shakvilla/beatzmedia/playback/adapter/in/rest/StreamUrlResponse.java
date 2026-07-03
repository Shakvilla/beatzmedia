package org.shakvilla.beatzmedia.playback.adapter.in.rest;

import java.time.Instant;

/**
 * Wire DTO for {@code GET /v1/tracks/:id/stream}. {@code previewSeconds} is present iff the
 * server decided PREVIEW (value {@code 30}); absent for FULL. Matches {@code API-CONTRACT.md} §4
 * and {@code Frontend/src/types/index.ts} (player context {@code previewSeconds}).
 */
public record StreamUrlResponse(String audioUrl, Integer previewSeconds, Instant expiresAt) {}
