package org.shakvilla.beatzmedia.media.domain;

/**
 * Projection returned to callers after upload. Carries enough info for the consuming module
 * (catalog/studio) to update its track/episode status and duration. ADD §3 / §6.
 * Duration is in whole seconds (INV/conventions §3).
 */
public record MediaHandle(
    MediaAssetId assetId, MediaKind kind, int durationSec, MediaStatus status) {}
