package org.shakvilla.beatzmedia.playback.application.port.in;

import java.time.Instant;
import java.util.Optional;

/**
 * Result of {@link GetStreamUrl}. {@code previewSeconds} is present iff the decision was PREVIEW
 * (value 30); absent for FULL. Field names mirror {@code StreamUrlResponse} in
 * {@code API-CONTRACT.md} §4 / Playback ADD §6.
 */
public record StreamUrlResult(String audioUrl, Optional<Integer> previewSeconds, Instant expiresAt) {}
