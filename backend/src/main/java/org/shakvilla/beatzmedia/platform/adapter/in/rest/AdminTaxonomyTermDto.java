package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import org.shakvilla.beatzmedia.platform.domain.TaxonomyTerm;

/**
 * A taxonomy term as the admin console sees it: the public shape plus {@code usageCount}.
 *
 * <p>The count is what lets the console disable a delete button and explain why, instead of firing
 * a request that comes back 409.
 */
public record AdminTaxonomyTermDto(
    String id,
    String kind,
    String slug,
    String label,
    String colorClass,
    int sortOrder,
    boolean active,
    long usageCount) {

  public static AdminTaxonomyTermDto from(TaxonomyTerm t, long usageCount) {
    return new AdminTaxonomyTermDto(
        t.id(),
        t.kind().wireValue(),
        t.slug(),
        t.label(),
        t.colorClass(),
        t.sortOrder(),
        t.active(),
        usageCount);
  }
}
