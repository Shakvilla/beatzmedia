package org.shakvilla.beatzmedia.identity.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.identity.application.port.in.AccountView;
import org.shakvilla.beatzmedia.identity.application.port.in.AuthResult;
import org.shakvilla.beatzmedia.identity.application.port.in.BrowserSessions;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.application.port.out.AuthSessionRepository;
import org.shakvilla.beatzmedia.identity.application.port.out.TokenIssuer;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AuthSession;
import org.shakvilla.beatzmedia.identity.domain.ResetTokenHash;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/** Renews short-lived access JWTs from an opaque, revocable browser session. */
@ApplicationScoped
public class BrowserSessionService implements BrowserSessions {
  private static final SecureRandom RANDOM = new SecureRandom();

  private final AuthSessionRepository sessions;
  private final AccountRepository accounts;
  private final TokenIssuer tokens;
  private final IdGenerator ids;
  private final Clock clock;
  private final Duration idleLimit;
  private final Duration maxLifetime;

  @Inject
  public BrowserSessionService(
      AuthSessionRepository sessions,
      AccountRepository accounts,
      TokenIssuer tokens,
      IdGenerator ids,
      Clock clock,
      @ConfigProperty(name = "beatz.auth.session-idle-seconds", defaultValue = "3600")
          long idleSeconds,
      @ConfigProperty(name = "beatz.auth.session-max-seconds", defaultValue = "2592000")
          long maxSeconds) {
    this.sessions = sessions;
    this.accounts = accounts;
    this.tokens = tokens;
    this.ids = ids;
    this.clock = clock;
    this.idleLimit = Duration.ofSeconds(idleSeconds);
    this.maxLifetime = Duration.ofSeconds(maxSeconds);
  }

  @Override
  @Transactional
  public String start(AccountId accountId) {
    byte[] secret = new byte[32];
    RANDOM.nextBytes(secret);
    String value = ids.newId() + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    Instant now = clock.now();
    sessions.save(new AuthSession(
        value.substring(0, value.indexOf('.')), accountId, ResetTokenHash.of(value),
        now, now, now.plus(maxLifetime), null));
    return value;
  }

  @Override
  @Transactional
  public Optional<AuthResult> renew(String cookieValue) {
    Optional<AuthSession> found = authenticatedSession(cookieValue);
    if (found.isEmpty()) return Optional.empty();

    AuthSession session = found.get();
    Instant now = clock.now();
    if (!session.activeAt(now, idleLimit)) {
      sessions.save(session.revokedAt(now));
      return Optional.empty();
    }

    Optional<Account> account = accounts.findById(session.accountId());
    if (account.isEmpty() || !account.get().canAuthenticate()) {
      sessions.save(session.revokedAt(now));
      return Optional.empty();
    }

    sessions.save(session.usedAt(now));
    Account current = account.get();
    Set<String> roles = new HashSet<>();
    roles.add("fan");
    if (current.isArtist()) roles.add("artist");
    if (current.isAdmin()) {
      accounts.findAllAdminMembers().stream()
          .filter(member -> member.accountId().equals(current.getId()))
          .findFirst()
          .ifPresent(member -> roles.add(member.role().wireValue()));
    }
    String token = tokens.issue(current.getId(), roles);
    AccountView view = new AccountView(
        current.getId().value(), current.getName(), current.getEmail(), current.getAvatar(),
        current.isArtist(), current.isAdmin());
    return Optional.of(new AuthResult(token, view));
  }

  @Override
  @Transactional
  public void revoke(String cookieValue) {
    authenticatedSession(cookieValue).ifPresent(session -> sessions.save(session.revokedAt(clock.now())));
  }

  private Optional<AuthSession> authenticatedSession(String value) {
    if (value == null || value.length() > 256) return Optional.empty();
    int separator = value.indexOf('.');
    if (separator < 1 || separator == value.length() - 1) return Optional.empty();
    return sessions.findForUpdate(value.substring(0, separator))
        .filter(session -> MessageDigest.isEqual(
            session.tokenHash().getBytes(StandardCharsets.US_ASCII),
            ResetTokenHash.of(value).getBytes(StandardCharsets.US_ASCII)));
  }
}
