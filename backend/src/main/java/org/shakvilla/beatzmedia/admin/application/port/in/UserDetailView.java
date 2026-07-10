package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.List;

/**
 * Response of {@code GET /admin/users/:id}: {@code { summary, activity, orders, devices,
 * actionLog } }, matching the {@code UserDetail} shape documented by API-CONTRACT.md §12 ("detail
 * (activity, orders, devices, action log)") — the wire contract, not {@code
 * Frontend/src/lib/admin-data.ts}'s locally-incomplete {@code UserDetail} TS interface (which
 * only lists activity/orders/devices). Admin ADD §6 (LLFR-ADMIN-02.1).
 *
 * <p><strong>Category A/B split (WU-ADM-2 as-built).</strong> {@code summary} and {@code
 * actionLog} are real (Category A: real account row + real {@code audit_entry} rows targeting
 * this account). {@code activity}, {@code orders}, {@code devices} are honest empty arrays
 * (Category B) — no per-account order-history-with-item-names port, device/session tracking
 * table, or unified activity-feed subsystem exists anywhere in this codebase; building any of
 * those is out of scope for this WU (would be its own WU). See admin.md §13 (WU-ADM-2 as-built)
 * for the full breakdown.
 */
public record UserDetailView(
    AdminUserRowView summary,
    List<Object> activity,
    List<Object> orders,
    List<Object> devices,
    List<ActionLogEntryView> actionLog) {}
