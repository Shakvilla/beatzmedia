package org.shakvilla.beatzmedia.admin.domain;

import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Catalog-moderation list filter for {@code GET /admin/catalog?status=}. Admin ADD §5.1
 * (LLFR-ADMIN-03.1).
 *
 * <p>The three wire values ({@code pending|published|takedown}) are illustrative buckets from the
 * PRD/ADD, not a 1:1 match with {@code catalog.domain.ReleaseStatus}'s five real states — the
 * bucket-to-status mapping is applied at the reading adapter (an Adapter-layer concern, since this
 * Domain-layer enum must not import another module's Domain type — ArchUnit's layered rule treats
 * {@code ..domain..} as one global layer only accessible from Application/Adapter). See {@code
 * CatalogAdminReaderAdapter} for the actual bucket→status-string mapping. {@code null} means "no
 * filter" (all releases) — same null-means-default convention as {@link UserFilter}.
 */
public enum CatalogFilter {
  PENDING,
  PUBLISHED,
  TAKEDOWN;

  /**
   * Parses the {@code ?status=} query parameter. Blank/missing → {@code null} (no filter). An
   * unrecognised value throws the generic {@link ValidationException} (422 {@code VALIDATION})
   * rather than a bespoke code — same "reuse the generic code" convention established by {@link
   * UserFilter#fromWireValue}.
   */
  public static CatalogFilter fromWireValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    for (CatalogFilter f : values()) {
      if (f.name().equalsIgnoreCase(value)) {
        return f;
      }
    }
    throw new ValidationException("Unknown catalog status filter: " + value, "status");
  }
}
