package org.shakvilla.beatzmedia.analytics.domain;

import java.time.Instant;

/**
 * A single staged settled-sale fact, appended when analytics observes a {@code SaleRecorded} event
 * (commerce). Owned exclusively by analytics — never a projection of a commerce/payments table row.
 * Framework-free. Analytics ADD §3.1 / §4.1 ({@code SettledSalesSource}).
 */
public record SaleFact(
    String id, String artistId, long grossMinor, String currency, Instant occurredAt, boolean processed) {

  /** A brand-new, not-yet-processed fact from a just-observed {@code SaleRecorded} event. */
  public static SaleFact unprocessed(String id, String artistId, long grossMinor, String currency, Instant occurredAt) {
    return new SaleFact(id, artistId, grossMinor, currency, occurredAt, false);
  }
}
