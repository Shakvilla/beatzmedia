package org.shakvilla.beatzmedia.studio.application.port.in;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * Input port: save the caller's own Studio settings (Category A fields only). Trigger: {@code PUT
 * /studio/settings}. Natural upsert (no {@code Idempotency-Key}: replaying the same request yields
 * the same saved state). Appends exactly one {@code AuditEntry} (INV-10). Studio ADD §4.1,
 * LLFR-STUDIO-04.2.
 */
public interface SaveStudioSettings {

  StudioSettingsView save(ArtistId artist, SaveStudioSettingsCommand cmd);
}
