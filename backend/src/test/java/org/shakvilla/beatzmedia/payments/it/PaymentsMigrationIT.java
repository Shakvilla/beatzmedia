package org.shakvilla.beatzmedia.payments.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.inject.Inject;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Migration integration test for the payments band: verifies the V701 migration applies cleanly and
 * validates on a fresh DB, and that the {@code idempotency_key} UNIQUE constraint is enforced (the
 * durable idempotency backstop, PRD §9.2). Payments ADD §9 / data-and-migrations §9.
 */
@QuarkusTest
@Tag("integration")
class PaymentsMigrationIT {

  @Inject
  Flyway flyway;

  @Inject
  AgroalDataSource dataSource;

  @Test
  void flyway_migrations_apply_cleanly_and_validate() {
    assertDoesNotThrow(() -> flyway.validate(),
        "Flyway validation must pass: V701 must apply on a fresh DB");
  }

  @Test
  void idempotency_key_unique_constraint_is_enforced() throws SQLException {
    String insert =
        "INSERT INTO payment_intent "
            + "(id, order_ref, amount_minor, currency, provider, method_kind, status, "
            + " idempotency_key, request_fingerprint, created_at, updated_at) "
            + "VALUES (?, 'BZ-2026-70001', 1000, 'GHS', 'mtn', 'momo', 'pending', "
            + " 'dup-key', 'fp', now(), now())";

    try (Connection c = dataSource.getConnection()) {
      c.setAutoCommit(true);
      try (Statement s = c.createStatement()) {
        assertEquals(
            1,
            s.executeUpdate(insert.replaceFirst("\\?", "'mig-it-1'")),
            "first insert with a fresh idempotency key succeeds");
      }
      try (Statement s = c.createStatement()) {
        assertThrows(
            SQLException.class,
            () -> s.executeUpdate(insert.replaceFirst("\\?", "'mig-it-2'")),
            "a second row with the same idempotency_key must be rejected by uq_payment_intent_idem");
      }
    }
  }
}
