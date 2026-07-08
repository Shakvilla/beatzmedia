package org.shakvilla.beatzmedia.studio.application.port.in;

import java.util.List;

/**
 * Command for {@code PUT /studio/profile} — {@link SaveStudioProfile}. Field names match {@code
 * SaveStudioProfileDto} (= {@code StudioProfileDto} minus server-managed fields — there are none
 * here, the whole shape is writable) per Studio ADD §6. Studio ADD §4.1 / LLFR-STUDIO-01.1.
 */
public record SaveStudioProfileCommand(
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
