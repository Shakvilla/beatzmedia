package org.shakvilla.beatzmedia.platform.fakes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Deterministic test fake for the {@link Clock} output port. Tests set a fixed instant; no
 * wall-clock calls. Testing-strategy §2.
 */
public class FakeClock implements Clock {

  private Instant now;

  private FakeClock(Instant now) {
    this.now = now;
  }

  public static FakeClock at(String isoInstant) {
    return new FakeClock(Instant.parse(isoInstant));
  }

  public static FakeClock at(Instant instant) {
    return new FakeClock(instant);
  }

  /** A fake fixed at a deterministic default instant for tests that don't care about the value. */
  public static FakeClock fixed() {
    return new FakeClock(Instant.parse("2026-06-22T12:00:00Z"));
  }

  @Override
  public Instant now() {
    return now;
  }

  @Override
  public LocalDate today(ZoneId zone) {
    return now.atZone(zone).toLocalDate();
  }

  /** Advance the clock by the given number of seconds. */
  public void advanceSeconds(long seconds) {
    this.now = now.plusSeconds(seconds);
  }

  /** Set the clock to a specific instant. */
  public void setNow(Instant instant) {
    this.now = instant;
  }
}
