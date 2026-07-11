package org.shakvilla.beatzmedia.admin.application.port.in;

import java.time.Instant;

/**
 * One row / detail view of a {@link org.shakvilla.beatzmedia.admin.domain.ModerationCase},
 * matching {@code ModerationItem} in {@code Frontend/src/lib/admin-data.ts}: {@code { id, item,
 * reporter, reason, age, severity, status }} — except {@code time} (ISO-8601) replaces the mock's
 * pre-formatted {@code age: string}, matching this module's established "real ISO timestamp over
 * a frontend-only cosmetic string" convention (same judgment call as {@code
 * ActionLogEntryView.time()} / {@code AuditEntry.time}). Admin ADD §6 (LLFR-ADMIN-04.1).
 *
 * <p>{@code item} is a human-readable label resolved from {@code targetRef} — {@code "Release ·
 * \"<title>\" by <artist>"} for catalog-sourced flags (the only source this WU builds); falls back
 * to the raw {@code targetRef} if the referenced release can no longer be resolved (same "fallback
 * to the raw id" precedent as {@code SupportTicketMapper}).
 */
public record ModerationCaseView(
    String id,
    String item,
    String reporter,
    String reason,
    Instant time,
    String severity,
    String status,
    boolean escalated) {}
