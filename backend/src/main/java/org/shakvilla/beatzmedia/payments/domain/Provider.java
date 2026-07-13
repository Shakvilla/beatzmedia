package org.shakvilla.beatzmedia.payments.domain;

/**
 * The payment rail a charge is routed to. Mirrors the frontend {@code Provider} enum (PRD §3.2 /
 * payments ADD §3). Pure Java, no framework imports.
 */
public enum Provider {
  mtn,
  telecel,
  airteltigo,
  card,
  bank;

  /**
   * Parse a wire/path string into a {@link Provider}, case-insensitively. Throws {@link
   * IllegalArgumentException} if unknown so the adapter can map it to a 4xx.
   */
  public static Provider fromWire(String value) {
    if (value == null) {
      throw new IllegalArgumentException("provider must not be null");
    }
    return Provider.valueOf(value.trim().toLowerCase());
  }

  /** True for the mobile-money rails (mtn/telecel/airteltigo), which Redde charges/pays directly. */
  public boolean isMomo() {
    return this == mtn || this == telecel || this == airteltigo;
  }
}
