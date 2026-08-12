package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.List;

/**
 * Response of {@code GET /admin/catalog/:id} and every catalog-moderation mutation ({@code
 * approve|flag|takedown|reinstate}) per admin ADD §5.1's REST table (all return {@code
 * CatalogItemDetail}): {@code { id, title, note, artist, type, status, upc, tracklist, splits,
 * actionLog }} — "tracklist, ISRC/UPC, rights/splits, action log" per admin ADD §6.12/§6's prose.
 *
 * <p>Category A/B split (WU-ADM-3 as-built, mirrors WU-ADM-1/WU-ADM-2's established convention):
 * {@code tracklist} (minus {@code isrc}), {@code splits} and {@code genre} are real; {@code note},
 * every track's {@code isrc}, and {@code upc} are honest {@code null} (Category B — no backing field
 * anywhere in {@code catalog}); {@code actionLog} is real, sourced from {@code audit_entry} rows
 * targeting this release id (same pattern as {@link UserDetailView#actionLog()}).
 *
 * <p>{@code genre} is nullable — {@code release.genre} has no NOT NULL constraint, so a release
 * submitted without one reads as unknown rather than as some default. It is served because the
 * moderation page previously printed a fixed literal ("Hiplife / Drill") beside every release
 * regardless of its actual genre, on the surface where a moderator decides approve-or-take-down.
 */
public record CatalogItemDetailView(
    String id,
    String title,
    String note,
    String artist,
    String type,
    String status,
    String upc,
    String genre,
    List<CatalogTrackView> tracklist,
    List<CatalogSplitView> splits,
    List<ActionLogEntryView> actionLog) {}
