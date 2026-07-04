package org.shakvilla.beatzmedia.podcasts.adapter.in.rest;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wire DTO for {@code GET /v1/podcasts/episodes/:id/stream}. {@code previewSeconds} is present iff
 * the server decided PREVIEW (value {@code 30}); the field is entirely ABSENT from the JSON (not
 * serialized as {@code null}) for FULL — mirrors playback's {@code StreamUrlResponse} (WU-PLY-1).
 * ADD §5.1 / API-CONTRACT.md §4.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StreamUrlResponse(String audioUrl, Integer previewSeconds, Instant expiresAt) {}
