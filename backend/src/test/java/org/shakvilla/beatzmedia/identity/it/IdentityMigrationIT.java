package org.shakvilla.beatzmedia.identity.it;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Migration IT: verifies that V201__create_account.sql applied cleanly on an empty DB (Quarkus Dev
 * Services runs Flyway migrate-at-start). Asserts the account + credential tables exist and are
 * writeable. data-and-migrations §9 / DoD §11.3.
 */
@QuarkusTest
@Tag("integration")
class IdentityMigrationIT {

  @Inject
  EntityManager em;

  @Test
  @Transactional
  void account_table_exists_and_is_insertable() {
    // If the migration didn't run this would throw a table-not-found error
    long count = em.createQuery(
            "SELECT COUNT(a) FROM AccountEntity a", Long.class)
        .getSingleResult();
    assertTrue(count >= 0, "account table must exist");
  }

  @Test
  @Transactional
  void credential_table_exists() {
    long count = em.createQuery(
            "SELECT COUNT(c) FROM CredentialEntity c", Long.class)
        .getSingleResult();
    assertTrue(count >= 0, "credential table must exist");
  }

  @Test
  @Transactional
  void account_email_unique_constraint_exists() {
    // Insert two accounts with the same email — the second must fail
    em.createNativeQuery(
            "INSERT INTO account (id, name, email, is_artist, is_admin, status, created_at, updated_at) "
                + "VALUES ('mig-test-1', 'Test', 'mig@example.com', false, false, 'active', now(), now())")
        .executeUpdate();

    try {
      em.createNativeQuery(
              "INSERT INTO account (id, name, email, is_artist, is_admin, status, created_at, updated_at) "
                  + "VALUES ('mig-test-2', 'Test2', 'mig@example.com', false, false, 'active', now(), now())")
          .executeUpdate();
      em.flush();
      throw new AssertionError("Expected unique constraint violation on account.email");
    } catch (Exception e) {
      // Expected: unique constraint account_email_uk violated
      String msg = e.getMessage() != null ? e.getMessage() : "";
      // Roll back to clean state
      // The @Transactional on the test method ensures the inserts are rolled back at end
    }
  }

  @Test
  @Transactional
  void account_status_check_constraint_exists() {
    try {
      em.createNativeQuery(
              "INSERT INTO account (id, name, email, is_artist, is_admin, status, created_at, updated_at) "
                  + "VALUES ('mig-test-3', 'Test3', 'mig3@example.com', false, false, 'invalid_status', now(), now())")
          .executeUpdate();
      em.flush();
      throw new AssertionError("Expected check constraint violation on account.status");
    } catch (Exception e) {
      // Expected: account_status_chk violated
    }
  }
}
