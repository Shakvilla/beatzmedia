package org.shakvilla.beatzmedia.platform.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Output port for the current time. Injected into domain/application services so time is testable
 * via {@code FakeClock}. ArchUnit enforces no direct calls to {@code Instant.now()} in core code.
 * Conventions §3, testing-strategy §6.
 */
public interface Clock {

  /** Current instant in UTC. */
  Instant now();

  /** Current date in the given zone. */
  LocalDate today(ZoneId zone);
}
