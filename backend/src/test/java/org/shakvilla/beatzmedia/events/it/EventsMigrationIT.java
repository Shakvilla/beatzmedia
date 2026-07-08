package org.shakvilla.beatzmedia.events.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import jakarta.inject.Inject;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Migration integration test: verifies the full Flyway migration set (including V952–V954
 * {@code event}/{@code ticket_tier}/{@code ticket} + the events rows in {@code
 * R__seed_dev_data.sql}) applies cleanly and validates on a fresh database. Events ADD §7 / §12 /
 * data-and-migrations §9.
 */
@QuarkusTest
@Tag("integration")
class EventsMigrationIT {

  @Inject
  Flyway flyway;

  @Test
  void flyway_migrations_apply_cleanly_and_validate() {
    // Flyway ran at start (quarkus.flyway.migrate-at-start=true).
    assertDoesNotThrow(
        () -> flyway.validate(),
        "Flyway validation must pass: V952-V954 + R__seed must apply on a fresh DB");
  }
}
