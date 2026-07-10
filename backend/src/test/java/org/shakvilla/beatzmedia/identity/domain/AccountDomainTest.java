package org.shakvilla.beatzmedia.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Account domain aggregate. Verifies invariants and fan-defaults.
 * LLFR-IDENTITY-01.1 (fan creation defaults).
 */
@Tag("unit")
class AccountDomainTest {

  private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");
  private static final AccountId ID = new AccountId("acc-1");
  private static final Credential CRED = new Credential(ID, "HASHED:secret");

  @Test
  void createFan_sets_isArtist_false() {
    Account account = Account.createFan(ID, "Alice", "alice@example.com", CRED, NOW);
    assertFalse(account.isArtist(), "New fan must not be an artist");
  }

  @Test
  void createFan_sets_isAdmin_false() {
    Account account = Account.createFan(ID, "Alice", "alice@example.com", CRED, NOW);
    assertFalse(account.isAdmin(), "New fan must not be an admin");
  }

  @Test
  void createFan_status_is_active() {
    Account account = Account.createFan(ID, "Alice", "alice@example.com", CRED, NOW);
    assertEquals(AccountStatus.active, account.getStatus());
  }

  @Test
  void createFan_avatar_is_null() {
    Account account = Account.createFan(ID, "Alice", "alice@example.com", CRED, NOW);
    assertNull(account.getAvatar());
  }

  @Test
  void createFan_timestamps_equal_now() {
    Account account = Account.createFan(ID, "Alice", "alice@example.com", CRED, NOW);
    assertEquals(NOW, account.getCreatedAt());
    assertEquals(NOW, account.getUpdatedAt());
  }

  @Test
  void createFan_credential_is_set() {
    Account account = Account.createFan(ID, "Alice", "alice@example.com", CRED, NOW);
    assertNotNull(account.getCredential());
    assertEquals("HASHED:secret", account.getCredential().getPasswordHash());
    assertEquals(Credential.ALGO_ARGON2ID, account.getCredential().getAlgo());
  }

  @Test
  void canAuthenticate_active_returns_true() {
    Account account = Account.createFan(ID, "Alice", "alice@example.com", CRED, NOW);
    assertTrue(account.canAuthenticate());
  }

  @Test
  void canAuthenticate_suspended_returns_false() {
    Account account = Account.createFan(ID, "Alice", "alice@example.com", CRED, NOW);
    account.suspend(NOW);
    assertFalse(account.canAuthenticate());
  }

  @Test
  void canAuthenticate_banned_returns_false() {
    Account account = Account.reconstitute(
        ID, "Alice", "alice@example.com", null, false, false, false, AccountStatus.banned, NOW, NOW,
        CRED);
    assertFalse(account.canAuthenticate());
  }

  @Test
  void accountId_rejects_blank_value() {
    try {
      new AccountId("  ");
      throw new AssertionError("Expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // correct
    }
  }
}
