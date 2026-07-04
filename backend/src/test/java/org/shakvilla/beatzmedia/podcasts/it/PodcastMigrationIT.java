package org.shakvilla.beatzmedia.podcasts.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import jakarta.inject.Inject;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Migration integration test: verifies the full Flyway migration set (including V945
 * {@code podcast}/{@code podcast_episode} + the podcasts rows in {@code R__seed_dev_data}) applies
 * cleanly and validates on a fresh database. Podcasts ADD §7 / data-and-migrations §9.
 */
@QuarkusTest
@Tag("integration")
class PodcastMigrationIT {

  @Inject
  Flyway flyway;

  @Test
  void flyway_migrations_apply_cleanly_and_validate() {
    // Flyway ran at start (quarkus.flyway.migrate-at-start=true).
    assertDoesNotThrow(
        () -> flyway.validate(),
        "Flyway validation must pass: V945 + R__seed must apply on a fresh DB");
  }
}
