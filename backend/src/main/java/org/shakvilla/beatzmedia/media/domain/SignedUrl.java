package org.shakvilla.beatzmedia.media.domain;

import java.time.Instant;

/**
 * Result of presigning a delivery object. Carries {@code url} (the time-boxed pre-signed GET URL),
 * {@code variant} (internal — FULL or PREVIEW, never sent to client), and {@code expiresAt}
 * (ISO-8601 UTC). Conventions §6 / ADD §3 / LLFR-MEDIA-01.3.
 */
public record SignedUrl(String url, DeliveryVariant variant, Instant expiresAt) {}
