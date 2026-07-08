package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * Wire-shaped press asset, shared by {@link StudioProfileView} (output) and {@link
 * SaveStudioProfileCommand} (input). Field names match {@code PressAsset} in {@code
 * Frontend/src/lib/studio-data.ts} / {@code API-CONTRACT.md}. Studio ADD §6.
 */
public record StudioPressAsset(String id, String name, String url) {}
