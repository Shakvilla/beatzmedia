package org.shakvilla.beatzmedia.studio.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import jakarta.inject.Inject;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Migration integration test: verifies that the Flyway migration set (V955 {@code studio_profile}
 * + R__ seed) applies cleanly and validates on a fresh database. Studio ADD §7 / §12.
 */
@QuarkusTest
@Tag("integration")
class StudioMigrationIT {

  @Inject
  Flyway flyway;

  @Test
  void flyway_migrations_apply_cleanly_and_validate() {
    // Flyway ran at start (quarkus.flyway.migrate-at-start=true).
    assertDoesNotThrow(() -> flyway.validate(),
        "Flyway validation must pass: V955 studio_profile + R__seed must apply on a fresh DB");
  }
}
