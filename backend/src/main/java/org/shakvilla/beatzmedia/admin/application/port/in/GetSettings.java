package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port: the platform settings for {@code GET /v1/admin/settings} (LLFR-ADMIN-10.1). Read-only;
 * nothing audited. Auth: super-admin only (enforced at the inbound resource). Admin ADD §4.1.
 */
public interface GetSettings {

  PlatformSettingsView get();
}
