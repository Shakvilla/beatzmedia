package org.shakvilla.beatzmedia.playback.adapter.in.rest;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wire DTO for {@code GET /v1/tracks/:id/stream}. {@code previewSeconds} is present iff the
 * server decided PREVIEW (value {@code 30}); the field is entirely ABSENT from the JSON (not
 * serialized as {@code null}) for FULL, per {@code API-CONTRACT.md} §4 and
 * {@code Frontend/src/types/index.ts} (optional {@code previewSeconds?}, player context).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StreamUrlResponse(String audioUrl, Integer previewSeconds, Instant expiresAt) {}
