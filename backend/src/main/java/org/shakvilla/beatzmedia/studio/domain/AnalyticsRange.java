package org.shakvilla.beatzmedia.studio.domain;

/**
 * The studio-analytics time window for {@code GET /studio/analytics}, mirroring {@code
 * Frontend/src/lib/studio-analytics.ts} {@code AnalyticsRange}. Studio ADD §3.
 *
 * <p>This is a studio-owned domain type, distinct from ({@code analytics.domain.AnalyticsRange}) —
 * studio never imports another module's domain types (hexagonal rule); the outbound {@code
 * AnalyticsReaderAdapter} translates between the two at the module boundary. Unlike {@code
 * analytics.domain.AnalyticsRange#fromWire}, which silently defaults on an unrecognised value
 * (that method serves analytics' own internal callers), {@link #fromWire(String)} here throws
 * {@link InvalidRangeException} (422 {@code INVALID_RANGE}) — this is the REST-facing parser for
 * the {@code ?range=} query param and the endpoint contract requires a hard error on garbage input.
 */
public enum AnalyticsRange {
  SEVEN_DAYS("7d"),
  TWENTY_EIGHT_DAYS("28d"),
  NINETY_DAYS("90d"),
  TWELVE_MONTHS("12m"),
  ALL("all");

  private final String wireValue;

  AnalyticsRange(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  /**
   * Parse a {@code ?range=} query value. {@code null}/blank defaults to {@link
   * #TWENTY_EIGHT_DAYS} (no range supplied); any other unrecognised value throws {@link
   * InvalidRangeException} (422 {@code INVALID_RANGE}).
   */
  public static AnalyticsRange fromWire(String value) {
    if (value == null || value.isBlank()) {
      return TWENTY_EIGHT_DAYS;
    }
    for (AnalyticsRange r : values()) {
      if (r.wireValue.equalsIgnoreCase(value)) {
        return r;
      }
    }
    throw new InvalidRangeException(value);
  }
}
