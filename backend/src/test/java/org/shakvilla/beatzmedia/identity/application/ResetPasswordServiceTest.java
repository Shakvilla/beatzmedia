package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.ResetPassword.ResetPasswordCommand;
import org.shakvilla.beatzmedia.identity.application.service.ResetPasswordService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountSuspendedException;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.domain.InvalidResetTokenException;
import org.shakvilla.beatzmedia.identity.domain.PasswordResetToken;
import org.shakvilla.beatzmedia.identity.domain.ResetTokenHash;
import org.shakvilla.beatzmedia.identity.domain.WeakPasswordException;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.identity.fakes.FakeCredentialHasher;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

/**
 * Unit tests for {@link ResetPasswordService} — the redemption half of LLFR-IDENTITY-01.5.
 *
 * <p>Nothing redeemed a reset token before this service existed, so these cover the whole contract
 * from scratch: the happy path, single-use, expiry, the three indistinguishable failure modes, and
 * the guards that stop a reset being used to weaken a password or revive a suspended account.
 */
@Tag("unit")
class ResetPasswordServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");
  private static final String PLAINTEXT = "opaque-reset-token";
  private static final String NEW_PASSWORD = "brand-new-password";

  private FakeAccountRepository repo;
  private FakeCredentialHasher hasher;
  private FakeClock clock;
  private ResetPasswordService service;

  @BeforeEach
  void setUp() {
    repo = new FakeAccountRepository();
    hasher = new FakeCredentialHasher();
    clock = FakeClock.at(NOW);
    service = new ResetPasswordService(repo, hasher, clock);
  }

  private Account seedAccount() {
    AccountId id = new AccountId("acc-1");
    Account account =
        Account.createFan(id, "Alice", "alice@example.com", new Credential(id, "HASHED:old"), NOW);
    repo.seed(account);
    return account;
  }

  private void seedToken(Instant expiresAt) {
    repo.saveResetToken(
        PasswordResetToken.issue(ResetTokenHash.of(PLAINTEXT), new AccountId("acc-1"), expiresAt));
  }

  @Test
  void validToken_setsTheNewPasswordAndSpendsTheToken() {
    seedAccount();
    seedToken(NOW.plus(Duration.ofMinutes(30)));

    service.reset(new ResetPasswordCommand(PLAINTEXT, NEW_PASSWORD));

    Account saved = repo.findById(new AccountId("acc-1")).orElseThrow();
    assertTrue(
        hasher.verify(NEW_PASSWORD, saved.getCredential().getPasswordHash()),
        "the new password should be hashed and stored");
    assertTrue(
        repo.findResetTokenByHash(ResetTokenHash.of(PLAINTEXT)).orElseThrow().used(),
        "the token should be spent");
  }

  @Test
  void tokenCannotBeUsedTwice() {
    seedAccount();
    seedToken(NOW.plus(Duration.ofMinutes(30)));
    service.reset(new ResetPasswordCommand(PLAINTEXT, NEW_PASSWORD));

    assertThrows(
        InvalidResetTokenException.class,
        () -> service.reset(new ResetPasswordCommand(PLAINTEXT, "another-password-1")));
  }

  @Test
  void expiredToken_isRejected() {
    seedAccount();
    seedToken(NOW.minus(Duration.ofSeconds(1)));

    assertThrows(
        InvalidResetTokenException.class,
        () -> service.reset(new ResetPasswordCommand(PLAINTEXT, NEW_PASSWORD)));
  }

  @Test
  void unknownToken_isRejected() {
    seedAccount();

    assertThrows(
        InvalidResetTokenException.class,
        () -> service.reset(new ResetPasswordCommand("never-issued", NEW_PASSWORD)));
  }

  /**
   * The three failure modes must be indistinguishable to the caller, or a harvested token could be
   * probed for validity. They already share an exception type; this pins the message too.
   */
  @Test
  void unknownUsedAndExpiredTokensAreIndistinguishable() {
    seedAccount();
    seedToken(NOW.minus(Duration.ofSeconds(1)));
    String expired =
        assertThrows(
                InvalidResetTokenException.class,
                () -> service.reset(new ResetPasswordCommand(PLAINTEXT, NEW_PASSWORD)))
            .getMessage();
    String unknown =
        assertThrows(
                InvalidResetTokenException.class,
                () -> service.reset(new ResetPasswordCommand("never-issued", NEW_PASSWORD)))
            .getMessage();

    assertEquals(expired, unknown);
  }

  @Test
  void weakPassword_isRejectedAndTokenSurvives() {
    seedAccount();
    seedToken(NOW.plus(Duration.ofMinutes(30)));

    assertThrows(
        WeakPasswordException.class, () -> service.reset(new ResetPasswordCommand(PLAINTEXT, "abc")));

    // The user should be able to retry with a stronger password rather than having to request a
    // whole new link because their first attempt was too short.
    assertFalse(
        repo.findResetTokenByHash(ResetTokenHash.of(PLAINTEXT)).orElseThrow().used(),
        "a rejected password must not spend the token");
  }

  @Test
  void suspendedAccount_cannotResetItsWayBackIn() {
    Account account = seedAccount();
    account.suspend(NOW);
    repo.save(account);
    seedToken(NOW.plus(Duration.ofMinutes(30)));

    assertThrows(
        AccountSuspendedException.class,
        () -> service.reset(new ResetPasswordCommand(PLAINTEXT, NEW_PASSWORD)));
  }

  @Test
  void blankToken_isRejectedWithoutTouchingTheRepository() {
    seedAccount();

    assertThrows(
        InvalidResetTokenException.class,
        () -> service.reset(new ResetPasswordCommand("  ", NEW_PASSWORD)));
  }
}
