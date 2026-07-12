package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * SLA/escalation summary for {@code GET /admin/moderation}, matching {@code MOD_SLA_HOURS}/{@code
 * MOD_ESCALATED} in {@code Frontend/src/lib/admin-data.ts}: {@code { openCount, slaHours,
 * escalatedCount }}. {@code openCount}/{@code escalatedCount} are real, whole-queue counts
 * (Category A, independent of the request's filters — same "always whole-table" semantics as
 * {@code UserCounts}); {@code slaHours} is the fixed {@link
 * org.shakvilla.beatzmedia.admin.domain.ModerationCase#DEFAULT_SLA_HOURS} constant. Admin ADD §6
 * (LLFR-ADMIN-04.1).
 */
public record ModerationSummaryView(long openCount, int slaHours, long escalatedCount) {}
