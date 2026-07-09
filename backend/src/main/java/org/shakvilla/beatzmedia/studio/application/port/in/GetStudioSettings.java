package org.shakvilla.beatzmedia.studio.application.port.in;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * Input port: read the caller's own Studio settings. Trigger: {@code GET /studio/settings}. Never
 * 404s — a not-yet-configured artist resolves to a blank shell (Category A) plus honest static
 * Category B defaults. Studio ADD §4.1, LLFR-STUDIO-04.2.
 */
public interface GetStudioSettings {

  StudioSettingsView get(ArtistId artist);
}
