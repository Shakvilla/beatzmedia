package org.shakvilla.beatzmedia.analytics.application.port.in;

import java.time.Instant;

/**
 * The time window a {@link RollupJob} tick recomputes. {@code asOf} is the instant the tick ran
 * (from the platform {@code Clock}) — recomputing the same window at a later {@code asOf} with the
 * same underlying facts must yield identical rows (idempotent upsert by {@code (artist, bucket,
 * grain)}, ADD §4.1).
 */
public record RollupWindow(Instant asOf) {

  public RollupWindow {
    if (asOf == null) {
      throw new IllegalArgumentException("asOf must not be null");
    }
  }
}
