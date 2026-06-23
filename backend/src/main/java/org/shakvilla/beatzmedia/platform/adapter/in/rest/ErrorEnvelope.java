package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import org.shakvilla.beatzmedia.platform.domain.ApiError;

/**
 * JSON wrapper for the uniform error envelope: {@code { "error": { "code", "message", "field?" } }}.
 * API-CONTRACT.md §1 / conventions §4.
 */
public record ErrorEnvelope(ApiError error) {}
