package org.shakvilla.beatzmedia.identity.application.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.application.port.in.ResetPassword;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.application.port.out.CredentialHasher;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.domain.InvalidResetTokenException;
import org.shakvilla.beatzmedia.identity.domain.PasswordResetToken;
import org.shakvilla.beatzmedia.identity.domain.ResetTokenHash;
import org.shakvilla.beatzmedia.identity.domain.WeakPasswordException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Application service for {@link ResetPassword} — the redemption half of the password-reset flow.
 *
 * <p><strong>Why this exists.</strong> {@code RequestPasswordResetService} minted single-use tokens
 * and {@code password_reset_token.used} was built to be flipped on redemption, but nothing ever
 * redeemed one. There was no service, no input port and no endpoint, so every reset produced a token
 * the user could not spend — account recovery was impossible for fans, artists and admins alike.
 *
 * <p><strong>Order of checks.</strong> Token validity is established before the password is looked
 * at, so an invalid token never reveals anything about password rules; and account state is checked
 * before the write, so a suspended account cannot regain access by resetting.
 *
 * <p><strong>Single use.</strong> The token is marked used in the same transaction as the password
 * change. Either both land or neither does, so a failed write can never leave a spent token behind.
 */
@ApplicationScoped
public class ResetPasswordService implements ResetPassword {

  /** Matches {@code RegisterFanService} — a reset must not be a way to set a weaker password. */
  private static final int MIN_PASSWORD_LENGTH = 8;

  private final AccountRepository accountRepository;
  private final CredentialHasher credentialHasher;
  private final Clock clock;

  @Inject
  public ResetPasswordService(
      AccountRepository accountRepository, CredentialHasher credentialHasher, Clock clock) {
    this.accountRepository = accountRepository;
    this.credentialHasher = credentialHasher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void reset(ResetPasswordCommand command) {
    if (command.token() == null || command.token().isBlank()) {
      throw new InvalidResetTokenException();
    }

    PasswordResetToken token =
        accountRepository
            .findResetTokenByHash(ResetTokenHash.of(command.token()))
            .orElseThrow(InvalidResetTokenException::new);

    Instant now = clock.now();
    if (token.used() || token.isExpired(now)) {
      throw new InvalidResetTokenException();
    }

    if (command.newPassword() == null || command.newPassword().length() < MIN_PASSWORD_LENGTH) {
      throw new WeakPasswordException();
    }

    // A token outliving its account is not something the caller should be able to detect, so this
    // is the same 410 as an unknown token rather than a 404.
    Account account =
        accountRepository.findById(token.accountId()).orElseThrow(InvalidResetTokenException::new);

    // Throws ACCOUNT_SUSPENDED for a suspended or banned account.
    account.resetPassword(new Credential(account.getId(), credentialHasher.hash(command.newPassword())), now);

    accountRepository.save(account);
    accountRepository.markResetTokenUsed(token.tokenHash());
  }
}
