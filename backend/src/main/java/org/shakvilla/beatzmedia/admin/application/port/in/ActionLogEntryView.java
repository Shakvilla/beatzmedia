package org.shakvilla.beatzmedia.admin.application.port.in;

import java.time.Instant;

/**
 * One row of {@code UserDetail.actionLog}: {@code { id, action, by, time }}. Real data (Category
 * A) — sourced from the {@code audit_entry} rows admin itself writes for this account id (verify/
 * suspend/reactivate/impersonate/data-export actions targeting this account), most recent first.
 * Admin ADD §6 (LLFR-ADMIN-02.1).
 */
public record ActionLogEntryView(String id, String action, String by, Instant time) {}
