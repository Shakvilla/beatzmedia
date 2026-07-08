package org.shakvilla.beatzmedia.studio.application.port.in;

import java.util.List;

/**
 * Read-model / DTO for a creator's Studio profile. Field names match {@code StudioProfile} in
 * {@code Frontend/src/lib/studio-data.ts} and {@code API-CONTRACT.md} §6 exactly — no {@code id} /
 * {@code artistId} on the wire; the caller's own profile is always resolved from the JWT subject.
 * Studio ADD §6 / LLFR-STUDIO-01.1.
 */
public record StudioProfileView(
    String displayName,
    String username,
    String hometown,
    List<String> genres,
    String bio,
    String avatar,
    String banner,
    StudioLinks links,
    List<StudioShow> shows,
    String featuredTrackId,
    String bookingEmail,
    List<StudioPressAsset> pressAssets) {}
