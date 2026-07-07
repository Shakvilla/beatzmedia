package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import org.shakvilla.beatzmedia.admin.domain.FeaturedSlot;

/**
 * Response DTO matching {@code FeaturedSlot} in {@code Frontend/src/lib/admin-data.ts}: {@code {
 * id, title, note, sponsored? }}. Admin ADD §6 / LLFR-ADMIN-06.1.
 */
public record FeaturedSlotDto(String id, String title, String note, boolean sponsored) {

  public static FeaturedSlotDto from(FeaturedSlot slot) {
    return new FeaturedSlotDto(slot.getId(), slot.getTitle(), slot.getNote(), slot.isSponsored());
  }
}
