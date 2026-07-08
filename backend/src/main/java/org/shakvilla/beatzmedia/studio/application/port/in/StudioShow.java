package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * Wire-shaped show appearance, shared by {@link StudioProfileView} (output) and {@link
 * SaveStudioProfileCommand} (input). Field names match {@code StudioShow} in {@code
 * Frontend/src/lib/studio-data.ts} / {@code API-CONTRACT.md}. Studio ADD §6.
 */
public record StudioShow(String id, String venue, String date, String city) {}
