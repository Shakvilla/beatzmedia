package org.shakvilla.beatzmedia.studio.application.port.in;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * Input port: {@code GET /studio/profile} — LLFR-STUDIO-01.1. Always resolves to the caller's own
 * profile ({@code artist} from the JWT subject, never a path param); never 404s — a
 * not-yet-configured profile resolves to a blank shell. Studio ADD §4.1.
 */
public interface GetStudioProfile {

  StudioProfileView get(ArtistId artist);
}
