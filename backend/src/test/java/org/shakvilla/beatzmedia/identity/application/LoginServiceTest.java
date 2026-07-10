package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.AuthResult;
import org.shakvilla.beatzmedia.identity.application.port.in.Login.LoginCommand;
import org.shakvilla.beatzmedia.identity.application.service.LoginService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountStatus;
import org.shakvilla.beatzmedia.identity.domain.AccountSuspendedException;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.domain.InvalidCredentialsException;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.identity.fakes.FakeCredentialHasher;
import org.shakvilla.beatzmedia.identity.fakes.FakeTokenIssuer;

/**
 * Unit tests for {@link LoginService}. Covers LLFR-IDENTITY-01.2 acceptance criteria.
 */
@Tag("unit")
class LoginServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");
  private static final String VALID_HASH = "HASHED:goodpassword";

  private FakeAccountRepository repo;
  private FakeCredentialHasher hasher;
  private FakeTokenIssuer tokenIssuer;
  private LoginService service;

  @BeforeEach
  void setUp() {
    repo = new FakeAccountRepository();
    hasher = new FakeCredentialHasher();
    tokenIssuer = new FakeTokenIssuer();
    service = new LoginService(repo, hasher, tokenIssuer);
  }

  private Account activeAccount(String email) {
    AccountId id = new AccountId("acc-1");
    Credential cred = new Credential(id, VALID_HASH);
    return Account.createFan(id, "Alice", email, cred, NOW);
  }

  private Account suspendedAccount(String email) {
    AccountId id = new AccountId("acc-2");
    Credential cred = new Credential(id, VALID_HASH);
    return Account.reconstitute(
        id, "Bob", email, null, false, false, false, AccountStatus.suspended, NOW, NOW, cred);
  }

  private Account bannedAccount(String email) {
    AccountId id = new AccountId("acc-3");
    Credential cred = new Credential(id, VALID_HASH);
    return Account.reconstitute(
        id, "Carol", email, null, false, false, false, AccountStatus.banned, NOW, NOW, cred);
  }

  // ---- LLFR-IDENTITY-01.2: success ----

  @Test
  void login_valid_credentials_returns_token_and_account() {
    repo.seed(activeAccount("alice@example.com"));

    AuthResult result = service.login(new LoginCommand("alice@example.com", "goodpassword"));

    assertNotNull(result.token());
    assertNotNull(result.account());
    assertEquals("alice@example.com", result.account().email());
  }

  @Test
  void login_success_token_contains_fan_role() {
    repo.seed(activeAccount("alice@example.com"));

    AuthResult result = service.login(new LoginCommand("alice@example.com", "goodpassword"));

    // FakeTokenIssuer format: "token:<id>:<roles>"
    String token = result.token();
    org.junit.jupiter.api.Assertions.assertTrue(token.contains("fan"));
  }

  // ---- LLFR-IDENTITY-01.2: unknown email → INVALID_CREDENTIALS (identical to wrong password) ----

  @Test
  void login_unknown_email_throws_InvalidCredentialsException() {
    assertThrows(InvalidCredentialsException.class,
        () -> service.login(new LoginCommand("nobody@example.com", "anypassword")));
  }

  @Test
  void login_wrong_password_throws_InvalidCredentialsException() {
    repo.seed(activeAccount("alice@example.com"));

    assertThrows(InvalidCredentialsException.class,
        () -> service.login(new LoginCommand("alice@example.com", "wrongpassword")));
  }

  @Test
  void login_unknown_email_and_wrong_password_produce_identical_exception() {
    repo.seed(activeAccount("alice@example.com"));

    InvalidCredentialsException unknownEmail = assertThrows(InvalidCredentialsException.class,
        () -> service.login(new LoginCommand("nobody@example.com", "anypassword")));

    InvalidCredentialsException wrongPassword = assertThrows(InvalidCredentialsException.class,
        () -> service.login(new LoginCommand("alice@example.com", "wrongpassword")));

    // Non-enumerating: same message and same error code — DoD §12.2
    assertEquals(unknownEmail.getMessage(), wrongPassword.getMessage());
    assertEquals(unknownEmail.getErrorCode(), wrongPassword.getErrorCode());
  }

  // ---- LLFR-IDENTITY-01.2: suspended account → ACCOUNT_SUSPENDED ----

  @Test
  void login_suspended_account_throws_AccountSuspendedException() {
    repo.seed(suspendedAccount("bob@example.com"));

    assertThrows(AccountSuspendedException.class,
        () -> service.login(new LoginCommand("bob@example.com", "goodpassword")));
  }

  @Test
  void login_banned_account_throws_AccountSuspendedException() {
    repo.seed(bannedAccount("carol@example.com"));

    assertThrows(AccountSuspendedException.class,
        () -> service.login(new LoginCommand("carol@example.com", "goodpassword")));
  }
}
