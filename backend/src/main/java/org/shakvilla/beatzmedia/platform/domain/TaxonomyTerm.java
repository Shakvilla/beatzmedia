package org.shakvilla.beatzmedia.platform.domain;

import java.util.Locale;

/**
 * One entry in an admin-managed controlled list — a genre, a podcast category, an event category or
 * a home browse tile.
 *
 * <p><strong>slug versus label.</strong> {@code slug} is the stable machine key and never changes
 * once created; {@code label} is what users see and what the consuming columns
 * ({@code release.genre}, {@code podcast.category}, {@code event.category}) actually store. Renaming
 * a term therefore rewrites {@code label} only, and the rename is propagated to those columns by the
 * application — see {@code TaxonomyService#rename}. Splitting the two is what makes a term
 * renameable at all: a taxonomy keyed on its display text cannot be corrected without breaking every
 * row that referenced the old spelling.
 *
 * <p>Domain type: no JPA, no Jakarta, no framework — the hexagonal dependency rule keeps this
 * importable from any module's application layer.
 */
public record TaxonomyTerm(
    String id,
    TaxonomyKind kind,
    String slug,
    String label,
    /** Tailwind gradient classes; only meaningful for {@link TaxonomyKind#BROWSE_CATEGORY}. */
    String colorClass,
    int sortOrder,
    /**
     * Inactive terms vanish from every picker but stay valid on rows that already reference them,
     * so deactivating a term never rewrites published content.
     */
    boolean active) {

  public TaxonomyTerm {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("TaxonomyTerm.id must not be blank");
    }
    if (kind == null) {
      throw new IllegalArgumentException("TaxonomyTerm.kind must not be null");
    }
    if (slug == null || slug.isBlank()) {
      throw new IllegalArgumentException("TaxonomyTerm.slug must not be blank");
    }
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("TaxonomyTerm.label must not be blank");
    }
  }

  /** A renamed copy. Only the label moves; the slug is the stable key and stays put. */
  public TaxonomyTerm withLabel(String newLabel) {
    return new TaxonomyTerm(id, kind, slug, newLabel, colorClass, sortOrder, active);
  }

  public TaxonomyTerm withActive(boolean nowActive) {
    return new TaxonomyTerm(id, kind, slug, label, colorClass, sortOrder, nowActive);
  }

  public TaxonomyTerm withSortOrder(int newOrder) {
    return new TaxonomyTerm(id, kind, slug, label, colorClass, newOrder, active);
  }

  public TaxonomyTerm withColorClass(String newColorClass) {
    return new TaxonomyTerm(id, kind, slug, label, newColorClass, sortOrder, active);
  }

  /**
   * Derives a URL-safe slug from a display label: {@code "News & Politics"} becomes
   * {@code "news-politics"}, {@code "R&B"} becomes {@code "rnb"}.
   *
   * <p>Ampersands are dropped rather than turned into separators, so {@code "R&B"} does not become
   * the misleading {@code "r-b"}. A label that reduces to nothing (all punctuation) yields an empty
   * string, which the service rejects rather than storing a blank key.
   */
  public static String slugify(String label) {
    String lower = label.toLowerCase(Locale.ROOT).replace("&", "");
    String hyphenated = lower.replaceAll("[^a-z0-9]+", "-");
    return hyphenated.replaceAll("^-+|-+$", "");
  }
}
