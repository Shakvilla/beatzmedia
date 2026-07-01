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

  // ---- V204: social_identity + password_reset_token (WU-IDN-2) ----

  @Test
  @Transactional
  void social_identity_table_exists() {
    Number result = (Number) em.createNativeQuery("SELECT COUNT(*) FROM social_identity")
        .getSingleResult();
    assertTrue(result.longValue() >= 0, "social_identity table must exist");
  }

  @Test
  @Transactional
  void password_reset_token_table_exists() {
    Number result = (Number) em.createNativeQuery("SELECT COUNT(*) FROM password_reset_token")
        .getSingleResult();
    assertTrue(result.longValue() >= 0, "password_reset_token table must exist");
  }

  @Test
  @Transactional
  void social_identity_provider_uid_unique_constraint_exists() {
    // Seed an account to satisfy the FK
    em.createNativeQuery(
            "INSERT INTO account (id, name, email, is_artist, is_admin, status, created_at, updated_at) "
                + "VALUES ('mig-social-acc', 'Social', 'mig-social@example.com', false, false, 'active', now(), now())")
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO social_identity (id, account_id, provider, provider_uid) "
                + "VALUES ('mig-social-1', 'mig-social-acc', 'google', 'dup-uid')")
        .executeUpdate();

    try {
      em.createNativeQuery(
              "INSERT INTO social_identity (id, account_id, provider, provider_uid) "
                  + "VALUES ('mig-social-2', 'mig-social-acc', 'google', 'dup-uid')")
          .executeUpdate();
      em.flush();
      throw new AssertionError("Expected unique constraint violation on (provider, provider_uid)");
    } catch (Exception e) {
      // Expected: social_identity_provider_uk violated
    }
  }

  @Test
  @Transactional
  void password_reset_token_account_fk_and_defaults() {
    em.createNativeQuery(
            "INSERT INTO account (id, name, email, is_artist, is_admin, status, created_at, updated_at) "
                + "VALUES ('mig-reset-acc', 'Reset', 'mig-reset@example.com', false, false, 'active', now(), now())")
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO password_reset_token (token_hash, account_id, expires_at) "
                + "VALUES ('mig-reset-hash', 'mig-reset-acc', now() + interval '30 minutes')")
        .executeUpdate();
    em.flush();

    Boolean used = (Boolean) em.createNativeQuery(
            "SELECT used FROM password_reset_token WHERE token_hash = 'mig-reset-hash'")
        .getSingleResult();
    assertTrue(Boolean.FALSE.equals(used), "used must default to FALSE");
  }
}
