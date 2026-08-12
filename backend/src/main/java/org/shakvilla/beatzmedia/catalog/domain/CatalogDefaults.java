package org.shakvilla.beatzmedia.catalog.domain;

/** Shared catalog defaults. */
public final class CatalogDefaults {

  /**
   * Cover art used until real artwork is uploaded.
   *
   * <p>This was {@code /images/placeholder.jpg}, duplicated as a literal in one service and a
   * constant in another — and the file did not exist. {@code Frontend/public/images/} was not in
   * the repository at all, so every track without artwork, and every newly provisioned artist,
   * rendered as a <em>broken</em> image rather than a neutral one.
   *
   * <p>SVG because it cannot fail to decode, stays sharp from a 40px track row to a 600px hero
   * without shipping multiple sizes, and is reviewable as text.
   *
   * <p>One constant, so the fallback can never again be right in one place and wrong in another.
   */
  public static final String PLACEHOLDER_IMAGE = "/images/placeholder.svg";

  private CatalogDefaults() {}
}
