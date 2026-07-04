package org.shakvilla.beatzmedia.podcasts.application.port.in;

import java.time.Instant;
import java.util.Optional;

/**
 * Result of resolving a signed episode stream URL. {@code previewSeconds} is present only when the
 * server has gated the caller to the preview rendition (INV-3). Mirrors playback's
 * {@code StreamUrlResult} (WU-PLY-1) for the episode-gating surface. ADD §6 / API-CONTRACT.md §4.
 */
public record StreamUrlResult(String audioUrl, Optional<Integer> previewSeconds, Instant expiresAt) {}
