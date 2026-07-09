package org.shakvilla.beatzmedia.studio.domain;

/**
 * The studio-audience time window for {@code GET /studio/audience}, mirroring {@code
 * Frontend/src/lib/studio-analytics.ts} {@code AudienceRange} — the same four short ranges as
 * {@link AnalyticsRange} but WITHOUT {@code all} (the audience endpoint's contract only lists
 * {@code 7d|28d|90d|12m}; {@code all} is a 422 {@code INVALID_RANGE} here). Studio ADD §3.
 */
public enum AudienceRange {
  SEVEN_DAYS("7d", AnalyticsRange.SEVEN_DAYS),
  TWENTY_EIGHT_DAYS("28d", AnalyticsRange.TWENTY_EIGHT_DAYS),
  NINETY_DAYS("90d", AnalyticsRange.NINETY_DAYS),
  TWELVE_MONTHS("12m", AnalyticsRange.TWELVE_MONTHS);

  private final String wireValue;
  private final AnalyticsRange analyticsRange;

  AudienceRange(String wireValue, AnalyticsRange analyticsRange) {
    this.wireValue = wireValue;
    this.analyticsRange = analyticsRange;
  }

  public String wireValue() {
    return wireValue;
  }

  /** The equivalent {@link AnalyticsRange} used to query the rollup data via the output port. */
  public AnalyticsRange toAnalyticsRange() {
    return analyticsRange;
  }

  /**
   * Parse a {@code ?range=} query value. {@code null}/blank defaults to {@link
   * #TWENTY_EIGHT_DAYS}; any other unrecognised value (including {@code "all"}, which {@link
   * AnalyticsRange} accepts but this narrower range does not) throws {@link
   * InvalidRangeException} (422 {@code INVALID_RANGE}).
   */
  public static AudienceRange fromWire(String value) {
    if (value == null || value.isBlank()) {
      return TWENTY_EIGHT_DAYS;
    }
    for (AudienceRange r : values()) {
      if (r.wireValue.equalsIgnoreCase(value)) {
        return r;
      }
    }
    throw new InvalidRangeException(value);
  }
}
