package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * Wire-shaped external profile links, shared by {@link StudioProfileView} (output) and {@link
 * SaveStudioProfileCommand} (input) — the shape is identical in both directions. Field names match
 * {@code StudioProfile.links} in {@code Frontend/src/lib/studio-data.ts} / {@code
 * API-CONTRACT.md}. Studio ADD §6.
 */
public record StudioLinks(String instagram, String twitter, String youtube, String website) {}
