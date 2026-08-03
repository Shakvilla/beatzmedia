package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import org.shakvilla.beatzmedia.platform.domain.TaxonomyTerm;

/**
 * Wire shape for a taxonomy term.
 *
 * <p>{@code label} is what every picker should display <em>and</em> submit, because the consuming
 * columns store the label. {@code slug} is exposed for stable client-side keys only.
 */
public record TaxonomyTermDto(
    String id,
    String kind,
    String slug,
    String label,
    String colorClass,
    int sortOrder,
    boolean active) {

  public static TaxonomyTermDto from(TaxonomyTerm t) {
    return new TaxonomyTermDto(
        t.id(), t.kind().wireValue(), t.slug(), t.label(), t.colorClass(), t.sortOrder(), t.active());
  }
}
