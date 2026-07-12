package org.shakvilla.beatzmedia.payments.domain;

/**
 * Time window for the admin finance overview (LLFR-ADMIN-05.1). Wire tokens match {@code AdminRange}
 * in {@code Frontend/src/lib/admin-data.ts} ({@code '24h' | '7d' | '30d'}) and the admin module's
 * {@code admin.domain.AdminRange} — but this is a payments-owned copy so the payments module stays
 * independent of {@code admin} (the hexagonal rule: modules never import each other's types).
 * Framework-free.
 */
public enum FinanceRange {
  TWENTY_FOUR_HOURS("24h", 1),
  SEVEN_DAYS("7d", 7),
  THIRTY_DAYS("30d", 30);

  private final String wire;
  private final int days;

  FinanceRange(String wire, int days) {
    this.wire = wire;
    this.days = days;
  }

  /** The trailing window length in days ({@code 24h} → 1). */
  public int days() {
    return days;
  }

  /** The wire token, e.g. {@code "7d"}. */
  public String wire() {
    return wire;
  }

  /**
   * Parse a wire token ({@code 24h|7d|30d}, case-insensitive; {@code null}/blank defaults to {@code
   * 7d}), throwing {@link InvalidRangeException} (→ 422 {@code INVALID_RANGE}) on an unrecognised value.
   */
  public static FinanceRange fromWire(String value) {
    if (value == null || value.isBlank()) {
      return SEVEN_DAYS;
    }
    String token = value.trim().toLowerCase();
    for (FinanceRange r : values()) {
      if (r.wire.equals(token)) {
        return r;
      }
    }
    throw new InvalidRangeException(value);
  }
}
