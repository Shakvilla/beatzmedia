package org.shakvilla.beatzmedia.admin.domain;

/**
 * The overview/health time window for {@code GET /admin/overview}, mirroring {@code
 * Frontend/src/lib/admin-data.ts}'s {@code AdminRange = '24h' | '7d' | '30d'}. Admin ADD §13
 * (WU-ADM-1) — a NEW, admin-owned enum, distinct from {@code analytics.domain.AnalyticsRange} and
 * {@code studio.domain.AnalyticsRange}/{@code AudienceRange} (`7d|28d|90d|12m|all`); admin never
 * imports another module's range type.
 *
 * <p>There is no hourly rollup grain in this codebase ({@code analytics.domain.Grain} is {@code
 * DAILY|WEEKLY|MONTHLY} only), so every {@link AdminRange} maps to {@link
 * org.shakvilla.beatzmedia.analytics.domain.Grain#DAILY}: {@code 24h} reads a single bucket
 * (today), {@code 7d} the trailing 7 daily buckets, {@code 30d} the trailing 30 daily buckets
 * (admin ADD §13 as-built).
 */
public enum AdminRange {
  TWENTY_FOUR_HOURS("24h", "last 24 hours", 1),
  SEVEN_DAYS("7d", "last 7 days", 7),
  THIRTY_DAYS("30d", "last 30 days", 30);

  private final String wireValue;
  private final String label;
  private final int days;

  AdminRange(String wireValue, String label, int days) {
    this.wireValue = wireValue;
    this.label = label;
    this.days = days;
  }

  public String wireValue() {
    return wireValue;
  }

  /** Human-readable {@code rangeLabel}, matching {@code admin-data.ts}'s {@code RANGE_META}. */
  public String label() {
    return label;
  }

  /** Number of trailing daily buckets that make up this range (and its comparison period). */
  public int days() {
    return days;
  }

  /**
   * Parses a {@code ?range=} query value. {@code null}/blank defaults to {@link #SEVEN_DAYS} (no
   * range supplied — mirrors {@code studio.domain.AnalyticsRange#fromWire}'s precedent of
   * defaulting on absence but throwing on garbage); any other unrecognised value throws {@link
   * InvalidAdminRangeException} (422 {@code INVALID_RANGE}).
   */
  public static AdminRange fromWireValue(String value) {
    if (value == null || value.isBlank()) {
      return SEVEN_DAYS;
    }
    for (AdminRange r : values()) {
      if (r.wireValue.equalsIgnoreCase(value)) {
        return r;
      }
    }
    throw new InvalidAdminRangeException(value);
  }
}
