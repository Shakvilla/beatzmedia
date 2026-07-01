package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.RequestPasswordReset.RequestPasswordResetCommand;
import org.shakvilla.beatzmedia.identity.application.service.RequestPasswordResetService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.domain.PasswordResetToken;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.identity.fakes.FakeMailer;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link RequestPasswordResetService}. Covers LLFR-IDENTITY-01.5 acceptance
 * criteria (identity ADD §11): any email is a no-op success; an existing email issues a
 * single-use, time-boxed, SHA-256-hashed reset token and mails it via {@code Mailer}.
 */
@Tag("unit")
class RequestPasswordResetServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");
  private static final long TTL_SECONDS = 1800;

  private FakeAccountRepository repo;
  private FakeMailer mailer;
  private FakeIds ids;
  private FakeClock clock;
  private RequestPasswordResetService service;

  @BeforeEach
  void setUp() {
    repo = new FakeAccountRepository();
    mailer = new FakeMailer();
    ids = FakeIds.sequential("rnd");
    clock = FakeClock.at(NOW);
    service = new RequestPasswordResetService(repo, mailer, ids, clock, TTL_SECONDS);
  }

  private Account seedAccount(String email) {
    AccountId id = new AccountId("acc-1");
    Credential cred = new Credential(id, "HASHED:pw");
    Account account = Account.createFan(id, "Alice", email, cred, NOW);
    repo.seed(account);
    return account;
  }

  @Test
  void unknown_email_is_silent_no_op() {
    service.request(new RequestPasswordResetCommand("nobody@example.com"));

    assertTrue(mailer.sent().isEmpty(), "No mail should be sent for an unknown email");
    assertTrue(repo.allResetTokens().isEmpty(), "No token should be issued for an unknown email");
  }

  @Test
  void existing_email_issues_token_and_sends_mail() {
    seedAccount("alice@example.com");

    service.request(new RequestPasswordResetCommand("alice@example.com"));

    assertEquals(1, mailer.sent().size());
    assertEquals("alice@example.com", mailer.sent().get(0).email());
    assertEquals(1, repo.allResetTokens().size());
  }

  @Test
  void issued_token_is_persisted_hashed_not_plaintext() {
    seedAccount("alice@example.com");

    service.request(new RequestPasswordResetCommand("alice@example.com"));

    String plaintextToken = mailer.sent().get(0).resetToken();
    PasswordResetToken persisted = repo.allResetTokens().get(0);

    assertNotEquals(plaintextToken, persisted.tokenHash(),
        "The persisted token must be a hash, never the plaintext");
    // SHA-256 hex digest is 64 characters
    assertEquals(64, persisted.tokenHash().length());
  }

  @Test
  void issued_token_is_unused_and_time_boxed() {
    seedAccount("alice@example.com");

    service.request(new RequestPasswordResetCommand("alice@example.com"));

    PasswordResetToken token = repo.allResetTokens().get(0);
    assertFalse(token.used());
    assertEquals(NOW.plusSeconds(TTL_SECONDS), token.expiresAt());
    assertFalse(token.isExpired(NOW), "Token must not be expired immediately after issue");
    assertTrue(token.isExpired(NOW.plusSeconds(TTL_SECONDS + 1)), "Token must expire after the TTL");
  }

  @Test
  void token_can_be_marked_used_exactly_once() {
    seedAccount("alice@example.com");
    service.request(new RequestPasswordResetCommand("alice@example.com"));
    PasswordResetToken token = repo.allResetTokens().get(0);

    repo.markResetTokenUsed(token.tokenHash());

    PasswordResetToken reloaded = repo.findResetTokenByHash(token.tokenHash()).orElseThrow();
    assertTrue(reloaded.used());
  }

  @Test
  void response_is_identical_shape_for_known_and_unknown_email() {
    // Non-enumeration at the service layer: request() never throws for either case, and the
    // caller (REST layer) always answers 204 regardless of outcome.
    seedAccount("alice@example.com");

    service.request(new RequestPasswordResetCommand("alice@example.com"));
    service.request(new RequestPasswordResetCommand("unknown@example.com"));

    // Both calls completed without throwing — asserted implicitly by reaching this line.
    assertEquals(1, mailer.sent().size(), "Only the known email should trigger a mail");
  }
}
