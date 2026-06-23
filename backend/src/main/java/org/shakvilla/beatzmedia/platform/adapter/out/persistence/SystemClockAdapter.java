package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * System-clock implementation of the {@link Clock} output port. Delegates to {@code
 * java.time.Instant.now()} (the only place in the codebase where wall-clock is called — ArchUnit
 * enforces this is only in adapter code). ADD §4.3.
 */
@ApplicationScoped
public class SystemClockAdapter implements Clock {

  @Override
  public Instant now() {
    return Instant.now();
  }

  @Override
  public LocalDate today(ZoneId zone) {
    return LocalDate.now(zone);
  }
}
