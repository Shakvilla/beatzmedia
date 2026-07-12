package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import org.shakvilla.beatzmedia.admin.application.port.in.CatalogItemRowView;

/**
 * Response DTO: one row of {@code GET /admin/catalog}'s {@code items}: {@code { id, title, note,
 * artist, type, tracks, status } }. Admin ADD §6 (LLFR-ADMIN-03.1).
 */
public record CatalogItemDto(
    String id, String title, String note, String artist, String type, int tracks, String status) {

  public static CatalogItemDto from(CatalogItemRowView view) {
    return new CatalogItemDto(
        view.id(), view.title(), view.note(), view.artist(), view.type(), view.tracks(),
        view.status());
  }
}
