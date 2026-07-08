package org.shakvilla.beatzmedia.studio.application.port.in;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * Input port: {@code PUT /studio/profile} — LLFR-STUDIO-01.1. Natural upsert (no {@code
 * Idempotency-Key}). Validates {@code genres ⊆ Genre} (422 {@code INVALID_GENRE}) and {@code
 * username} uniqueness (409 {@code USERNAME_TAKEN}, field {@code username}). Studio ADD §4.1 / §9.
 */
public interface SaveStudioProfile {

  StudioProfileView save(ArtistId artist, SaveStudioProfileCommand cmd);
}
