package org.shakvilla.beatzmedia.admin.application.port.in;

import org.shakvilla.beatzmedia.admin.domain.ModReason;
import org.shakvilla.beatzmedia.admin.domain.ModStatus;

/**
 * Query parameters for {@code GET /admin/moderation?status=&type=}. {@code type} filters by
 * {@link ModReason} — matching {@code admin-data.ts}'s type-chip semantics ({@code MOD_TYPES:
 * ModReason[]}), not a target-content-type dimension (no such field exists on {@code
 * ModerationCase}). Admin ADD §5.1 (LLFR-ADMIN-04.1).
 */
public record ModQuery(ModStatus status, ModReason type) {}
