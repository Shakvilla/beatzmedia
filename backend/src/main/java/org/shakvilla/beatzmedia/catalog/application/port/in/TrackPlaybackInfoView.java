package org.shakvilla.beatzmedia.catalog.application.port.in;

/**
 * Minimal, caller-agnostic projection of a track for cross-module playback decisions: existence +
 * intrinsic commercial kind only (no per-caller price/ownership decoration — that is resolved by
 * the calling module itself, e.g. playback's own {@code OwnershipReader}). Catalog ADD §4.1.
 *
 * @param ownership wire-free: {@code "free"} or {@code "for-sale"} (never {@code "owned"} — this
 *     port never decorates per-caller)
 */
public record TrackPlaybackInfoView(String id, String ownership) {}
