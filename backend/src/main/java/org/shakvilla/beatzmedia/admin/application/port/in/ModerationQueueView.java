package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.List;

/**
 * Response of {@code GET /admin/moderation}: {@code { items, page, size, total, summary }}. Admin
 * ADD §6 (LLFR-ADMIN-04.1).
 */
public record ModerationQueueView(
    List<ModerationCaseView> items,
    int page,
    int size,
    long total,
    ModerationSummaryView summary) {}
