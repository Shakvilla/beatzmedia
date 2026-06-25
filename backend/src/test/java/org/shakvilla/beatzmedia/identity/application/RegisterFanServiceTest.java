package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.AuthResult;
import org.shakvilla.beatzmedia.identity.application.port.in.RegisterFan.RegisterFanCommand;
import org.shakvilla.beatzmedia.identity.application.service.RegisterFanService;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.domain.EmailTakenException;
import org.shakvilla.beatzmedia.identity.domain.WeakPasswordException;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.identity.fakes.FakeCredentialHasher;
import org.shakvilla.beatzmedia.identity.fakes.FakeTokenIssuer;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link RegisterFanService}. Covers LLFR-IDENTITY-01.1 acceptance criteria.
 */
@Tag("unit")
class RegisterFanServiceTest {

  private FakeAccountRepository repo;
  private FakeCredentialHasher hasher;
  private FakeTokenIssuer tokenIssuer;
  private FakeIds ids;
  private FakeClock clock;
  private RegisterFanService service;

  @BeforeEach
  void setUp() {
    repo = new FakeAccountRepository();
    hasher = new FakeCredentialHasher();
    tokenIssuer = new FakeTokenIssuer();
    ids = FakeIds.sequential("acc");
    clock = FakeClock.fixed();
    service = new RegisterFanService(repo, hasher, tokenIssuer, ids, clock);
  }

  // ---- LLFR-IDENTITY-01.1: success ----

  @Test
  void register_success_returns_201_shape() {
    AuthResult result = service.register(
        new RegisterFanCommand("Alice", "alice@example.com", "password123"));

    assertNotNull(result.token(), "Token must be present");
    assertNotNull(result.account(), "Account must be present");
    assertEquals("alice@example.com", result.account().email());
    assertEquals("Alice", result.account().name());
    assertFalse(result.account().isArtist(), "Fan must not be an artist");
    assertFalse(result.account().isAdmin(), "Fan must not be an admin");
  }

  @Test
  void register_success_stores_account_in_repository() {
    service.register(new RegisterFanCommand("Bob", "bob@example.com", "password123"));
    assertEquals(1, repo.all().size());
    assertEquals("bob@example.com", repo.all().get(0).getEmail());
  }

  @Test
  void register_success_hashes_password_not_plain() {
    service.register(new RegisterFanCommand("Carol", "carol@example.com", "password123"));
    Credential cred = repo.all().get(0).getCredential();
    assertNotNull(cred);
    // FakeCredentialHasher prefixes with "HASHED:" — must not be the plain password
    assertFalse(cred.getPasswordHash().equals("password123"));
  }

  @Test
  void register_token_contains_fan_role() {
    AuthResult result = service.register(
        new RegisterFanCommand("Dave", "dave@example.com", "password123"));
    // FakeTokenIssuer format: "token:<id>:<roles>"
    String token = result.token();
    assertNotNull(token);
    org.junit.jupiter.api.Assertions.assertTrue(token.contains("fan"), "Token must carry fan role");
  }

  // ---- LLFR-IDENTITY-01.1: duplicate email → EMAIL_TAKEN ----

  @Test
  void register_duplicate_email_throws_EmailTakenException() {
    service.register(new RegisterFanCommand("Alice", "alice@example.com", "password123"));

    assertThrows(EmailTakenException.class,
        () -> service.register(
            new RegisterFanCommand("Alice2", "alice@example.com", "password456")));
  }

  @Test
  void register_duplicate_email_is_case_insensitive() {
    service.register(new RegisterFanCommand("Alice", "alice@example.com", "password123"));

    assertThrows(EmailTakenException.class,
        () -> service.register(
            new RegisterFanCommand("Alice2", "ALICE@EXAMPLE.COM", "password456")));
  }

  @Test
  void register_duplicate_email_does_not_create_second_account() {
    service.register(new RegisterFanCommand("Alice", "alice@example.com", "password123"));
    try {
      service.register(new RegisterFanCommand("Alice2", "alice@example.com", "password456"));
    } catch (EmailTakenException ignored) {
      // expected
    }
    assertEquals(1, repo.all().size(), "Only one account must exist");
  }

  // ---- LLFR-IDENTITY-01.1: password < 8 chars → WEAK_PASSWORD ----

  @Test
  void register_password_less_than_8_chars_throws_WeakPasswordException() {
    assertThrows(WeakPasswordException.class,
        () -> service.register(new RegisterFanCommand("Eve", "eve@example.com", "short")));
  }

  @Test
  void register_password_exactly_7_chars_is_weak() {
    assertThrows(WeakPasswordException.class,
        () -> service.register(new RegisterFanCommand("Frank", "frank@example.com", "1234567")));
  }

  @Test
  void register_password_exactly_8_chars_is_accepted() {
    // Should NOT throw
    service.register(new RegisterFanCommand("Grace", "grace@example.com", "12345678"));
    assertEquals(1, repo.all().size());
  }

  @Test
  void register_null_password_throws_WeakPasswordException() {
    assertThrows(WeakPasswordException.class,
        () -> service.register(new RegisterFanCommand("Heidi", "heidi@example.com", null)));
  }
}
