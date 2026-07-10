package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.AuthResult;
import org.shakvilla.beatzmedia.identity.application.port.in.SocialLogin.SocialLoginCommand;
import org.shakvilla.beatzmedia.identity.application.port.out.SocialVerifier.VerifiedIdentity;
import org.shakvilla.beatzmedia.identity.application.service.SocialLoginService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountStatus;
import org.shakvilla.beatzmedia.identity.domain.AccountSuspendedException;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.domain.SocialProvider;
import org.shakvilla.beatzmedia.identity.domain.SocialTokenInvalidException;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.identity.fakes.FakeSocialVerifier;
import org.shakvilla.beatzmedia.identity.fakes.FakeTokenIssuer;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link SocialLoginService}. Covers LLFR-IDENTITY-01.3 acceptance criteria
 * (identity ADD §11): a valid Google token for a new email creates and links a fan account;
 * repeat login with the same linked identity resolves to the same account; an invalid token
 * throws SOCIAL_TOKEN_INVALID.
 */
@Tag("unit")
class SocialLoginServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");

  private FakeAccountRepository repo;
  private FakeSocialVerifier verifier;
  private FakeTokenIssuer tokenIssuer;
  private FakeIds ids;
  private FakeClock clock;
  private SocialLoginService service;

  @BeforeEach
  void setUp() {
    repo = new FakeAccountRepository();
    verifier = new FakeSocialVerifier();
    tokenIssuer = new FakeTokenIssuer();
    ids = FakeIds.sequential("id");
    clock = FakeClock.at(NOW);
    service = new SocialLoginService(repo, verifier, tokenIssuer, ids, clock, new FakeEvent<>());
  }

  @Test
  void new_google_email_creates_and_links_fan_account() {
    verifier.register(SocialProvider.GOOGLE, "good-token",
        new VerifiedIdentity("google-uid-1", "newuser@example.com", "New User", null));

    AuthResult result = service.socialLogin(new SocialLoginCommand(SocialProvider.GOOGLE, "good-token"));

    assertNotNull(result.token());
    assertEquals("newuser@example.com", result.account().email());
    assertFalse(result.account().isArtist());
    assertFalse(result.account().isAdmin());

    // Account was actually created and a fan (no password credential)
    Account created = repo.findByEmail("newuser@example.com").orElseThrow();
    assertEquals(AccountStatus.active, created.getStatus());

    // Social identity link was persisted
    assertTrue(repo.allSocialIdentities().stream()
        .anyMatch(s -> s.getProvider() == SocialProvider.GOOGLE
            && s.getProviderUid().equals("google-uid-1")));
  }

  @Test
  void repeat_social_login_with_same_token_resolves_to_same_account() {
    verifier.register(SocialProvider.GOOGLE, "good-token",
        new VerifiedIdentity("google-uid-1", "newuser@example.com", "New User", null));

    AuthResult first = service.socialLogin(new SocialLoginCommand(SocialProvider.GOOGLE, "good-token"));
    AuthResult second = service.socialLogin(new SocialLoginCommand(SocialProvider.GOOGLE, "good-token"));

    assertEquals(first.account().id(), second.account().id());
    // Only one social_identity link and one account should exist
    assertEquals(1, repo.allSocialIdentities().size());
    assertEquals(1, repo.all().size());
  }

  @Test
  void existing_email_is_linked_not_duplicated() {
    AccountId existingId = new AccountId("acc-existing");
    Credential cred = new Credential(existingId, "HASHED:pw");
    repo.seed(Account.createFan(existingId, "Existing Fan", "existing@example.com", cred, NOW));

    verifier.register(SocialProvider.FACEBOOK, "fb-token",
        new VerifiedIdentity("fb-uid-1", "existing@example.com", "Existing Fan", null));

    AuthResult result = service.socialLogin(new SocialLoginCommand(SocialProvider.FACEBOOK, "fb-token"));

    assertEquals("acc-existing", result.account().id());
    assertEquals(1, repo.all().size(), "must not create a duplicate account");
  }

  @Test
  void invalid_token_throws_SocialTokenInvalidException() {
    assertThrows(SocialTokenInvalidException.class,
        () -> service.socialLogin(new SocialLoginCommand(SocialProvider.GOOGLE, "bad-token")));
  }

  @Test
  void suspended_linked_account_throws_AccountSuspendedException() {
    AccountId id = new AccountId("acc-suspended");
    repo.seed(Account.reconstitute(
        id, "Suspended", "susp@example.com", null, false, false, false, AccountStatus.suspended,
        NOW, NOW, null));
    verifier.register(SocialProvider.GOOGLE, "tok",
        new VerifiedIdentity("google-uid-susp", "susp@example.com", "Suspended", null));

    assertThrows(AccountSuspendedException.class,
        () -> service.socialLogin(new SocialLoginCommand(SocialProvider.GOOGLE, "tok")));
  }

  // ---- Minimal fake CDI Event for unit tests (mirrors RegisterFanServiceTest.FakeEvent) ----

  /**
   * Lightweight stand-in for {@link jakarta.enterprise.event.Event} that records all fired
   * payloads. Used in unit tests to assert event publication without a CDI container.
   */
  static final class FakeEvent<T> implements jakarta.enterprise.event.Event<T> {

    private final List<T> firedEvents = new ArrayList<>();

    @Override
    public void fire(T event) {
      firedEvents.add(event);
    }

    @Override
    public <U extends T> java.util.concurrent.CompletionStage<U> fireAsync(U event) {
      firedEvents.add(event);
      return java.util.concurrent.CompletableFuture.completedFuture(event);
    }

    @Override
    public <U extends T> java.util.concurrent.CompletionStage<U> fireAsync(
        U event, jakarta.enterprise.event.NotificationOptions options) {
      firedEvents.add(event);
      return java.util.concurrent.CompletableFuture.completedFuture(event);
    }

    @Override
    public <U extends T> jakarta.enterprise.event.Event<U> select(
        jakarta.enterprise.util.TypeLiteral<U> subtype,
        java.lang.annotation.Annotation... qualifiers) {
      @SuppressWarnings("unchecked")
      FakeEvent<U> cast = (FakeEvent<U>) this;
      return cast;
    }

    @Override
    public <U extends T> jakarta.enterprise.event.Event<U> select(
        Class<U> subtype, java.lang.annotation.Annotation... qualifiers) {
      @SuppressWarnings("unchecked")
      FakeEvent<U> cast = (FakeEvent<U>) this;
      return cast;
    }

    @Override
    public jakarta.enterprise.event.Event<T> select(java.lang.annotation.Annotation... qualifiers) {
      return this;
    }

    List<T> fired() {
      return List.copyOf(firedEvents);
    }
  }
}
