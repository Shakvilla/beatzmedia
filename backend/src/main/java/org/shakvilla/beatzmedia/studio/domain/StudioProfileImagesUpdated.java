package org.shakvilla.beatzmedia.studio.domain;

import java.time.Instant;

/** Published after persistent avatar/banner URLs have been saved to the studio profile. */
public record StudioProfileImagesUpdated(
    String artistId, String avatarUrl, String bannerUrl, Instant updatedAt) {}
