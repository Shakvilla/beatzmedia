package org.shakvilla.beatzmedia.payments.domain;

/**
 * The kind of payment/payout method. Mirrors the frontend {@code MethodKind} (payments ADD §3).
 * Pure Java, no framework imports.
 */
public enum MethodKind {
  momo,
  bank,
  card;

  /** Parse case-insensitively; throws {@link IllegalArgumentException} on an unknown value. */
  public static MethodKind fromWire(String value) {
    if (value == null) {
      throw new IllegalArgumentException("method kind must not be null");
    }
    return MethodKind.valueOf(value.trim().toLowerCase());
  }
}
