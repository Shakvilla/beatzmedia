package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port: save platform settings for {@code PUT /v1/admin/settings} (LLFR-ADMIN-10.1). Updates
 * the scalar settings (fee %, payout day/minimum, currency, maintenance mode) and the real feature
 * flags, then appends exactly one {@code AuditEntry} (INV-10, {@code SETTINGS}). Changing {@code
 * platformFeePct} is forward-only — payments reads it at settle time, so settled sales are never
 * re-priced. Auth: super-admin only. Admin ADD §4.1.
 */
public interface SaveSettings {

  PlatformSettingsView save(String actorId, PlatformSettingsInput input);
}
