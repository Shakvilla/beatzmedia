package org.shakvilla.beatzmedia.search.domain;

/**
 * Ranking input; excludes flagged bot plays (LLFR-SEARCH-01.2 / INV-SRCH-3).
 * The search module trusts the upstream-supplied value and never inflates it.
 */
public record Popularity(long score) {
  public static final Popularity ZERO = new Popularity(0L);

  public Popularity {
    if (score < 0) throw new IllegalArgumentException("popularity score must be >= 0");
  }
}
