package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * One row of {@code GET /admin/catalog}'s {@code items} list, matching {@code CatalogItem} in
 * {@code Frontend/src/lib/admin-data.ts}: {@code { id, title, note?, artist, type, tracks,
 * status }}. Admin ADD §6 (LLFR-ADMIN-03.1).
 *
 * <p>{@code note} is always {@code null} (Category B — no backing subsystem for a free-text
 * submission/flag annotation on a release; see admin ADD §13, WU-ADM-3 as-built). {@code status}
 * is the REAL {@code catalog.domain.ReleaseStatus} wire value ({@code
 * draft|in_review|scheduled|live|takedown}), not the frontend mock's narrower {@code CatalogStatus}
 * union — same "prefer the real domain enum over the illustrative mock" judgment call as WU-ADM-2.
 */
public record CatalogItemRowView(
    String id, String title, String note, String artist, String type, int tracks, String status) {}
