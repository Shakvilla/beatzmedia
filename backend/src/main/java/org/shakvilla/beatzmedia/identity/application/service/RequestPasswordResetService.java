package org.shakvilla.beatzmedia.identity.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.identity.application.port.in.RequestPasswordReset;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.application.port.out.Mailer;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.PasswordResetToken;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-IDENTITY-01.5 (password reset request). Always completes
 * successfully from the caller's point of view — an unknown email is a silent no-op — so the
 * response never reveals whether an address is registered (non-enumeration, DoD §12.2).
 *
 * <p>Security: the plaintext token is generated here, mailed via the {@link Mailer} port, and then
 * discarded; only its SHA-256 hash is persisted ({@link PasswordResetToken#tokenHash()}). Identity
 * ADD §4.1 / §9.
 */
@ApplicationScoped
public class RequestPasswordResetService implements RequestPasswordReset {

  private final AccountRepository accountRepository;
  private final Mailer mailer;
  private final IdGenerator idGenerator;
  private final Clock clock;
  private final long resetTtlSeconds;

  @Inject
  public RequestPasswordResetService(
      AccountRepository accountRepository,
      Mailer mailer,
      IdGenerator idGenerator,
      Clock clock,
      @ConfigProperty(name = "beatz.identity.password-reset-ttl-seconds", defaultValue = "1800")
          long resetTtlSeconds) {
    this.accountRepository = accountRepository;
    this.mailer = mailer;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.resetTtlSeconds = resetTtlSeconds;
  }

  @Override
  @Transactional
  public void request(RequestPasswordResetCommand command) {
    Optional<Account> maybeAccount = accountRepository.findByEmail(command.email());
    if (maybeAccount.isEmpty()) {
      // Non-enumerating: silent no-op for unknown email — DoD §12.2 / LLFR-IDENTITY-01.5.
      return;
    }

    Account account = maybeAccount.get();

    // Opaque, high-entropy plaintext token — generated fresh per request, never persisted.
    String plaintextToken = idGenerator.newId() + idGenerator.newId();
    String tokenHash = sha256Hex(plaintextToken);

    Instant expiresAt = clock.now().plus(Duration.ofSeconds(resetTtlSeconds));
    PasswordResetToken token = PasswordResetToken.issue(tokenHash, account.getId(), expiresAt);
    accountRepository.saveResetToken(token);

    mailer.sendPasswordReset(account.getEmail(), plaintextToken);
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is guaranteed available on every JVM per the platform spec.
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
