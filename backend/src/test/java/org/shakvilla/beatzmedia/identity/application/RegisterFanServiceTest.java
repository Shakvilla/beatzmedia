package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.AuthResult;
import org.shakvilla.beatzmedia.identity.application.port.in.RegisterFan.RegisterFanCommand;
import org.shakvilla.beatzmedia.identity.application.service.RegisterFanService;
import org.shakvilla.beatzmedia.identity.domain.AccountRegistered;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.domain.EmailTakenException;
import org.shakvilla.beatzmedia.identity.domain.WeakPasswordException;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.identity.fakes.FakeCredentialHasher;
import org.shakvilla.beatzmedia.identity.fakes.FakeTokenIssuer;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link RegisterFanService}. Covers LLFR-IDENTITY-01.1 acceptance criteria,
 * including the AccountRegistered event firing requirement (B1).
 */
@Tag("unit")
class RegisterFanServiceTest {

  private FakeAccountRepository repo;
  private FakeCredentialHasher hasher;
  private FakeTokenIssuer tokenIssuer;
  private FakeIds ids;
  private FakeClock clock;
  private FakeEvent<AccountRegistered> fakeEvent;
  private RegisterFanService service;

  @BeforeEach
  void setUp() {
    repo = new FakeAccountRepository();
    hasher = new FakeCredentialHasher();
    tokenIssuer = new FakeTokenIssuer();
    ids = FakeIds.sequential("acc");
    clock = FakeClock.fixed();
    fakeEvent = new FakeEvent<>();
    service = new RegisterFanService(repo, hasher, tokenIssuer, ids, clock, fakeEvent);
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
    assertTrue(token.contains("fan"), "Token must carry fan role");
  }

  // ---- B1: AccountRegistered event ----

  @Test
  void register_success_fires_exactly_one_AccountRegistered_event() {
    service.register(new RegisterFanCommand("Alice", "alice@example.com", "password123"));

    assertEquals(1, fakeEvent.fired().size(), "Exactly one AccountRegistered event must be fired");
  }

  @Test
  void register_success_event_carries_correct_accountId_and_email() {
    AuthResult result = service.register(
        new RegisterFanCommand("Alice", "alice@example.com", "password123"));

    AccountRegistered event = fakeEvent.fired().get(0);
    assertEquals(result.account().id(), event.accountId(), "Event accountId must match returned account id");
    assertEquals("alice@example.com", event.email(), "Event email must match signup email");
    assertEquals("Alice", event.name(), "Event name must match signup name");
    assertNotNull(event.registeredAt(), "Event registeredAt must not be null");
  }

  @Test
  void register_duplicate_email_fires_no_AccountRegistered_event() {
    service.register(new RegisterFanCommand("Alice", "alice@example.com", "password123"));
    fakeEvent.clear(); // clear the first successful registration's event

    try {
      service.register(new RegisterFanCommand("Alice2", "alice@example.com", "password456"));
    } catch (EmailTakenException ignored) {
      // expected
    }

    assertEquals(0, fakeEvent.fired().size(),
        "AccountRegistered must NOT be fired on duplicate-email (EMAIL_TAKEN) attempt");
  }

  @Test
  void register_weak_password_fires_no_AccountRegistered_event() {
    try {
      service.register(new RegisterFanCommand("Eve", "eve@example.com", "short"));
    } catch (WeakPasswordException ignored) {
      // expected
    }

    assertEquals(0, fakeEvent.fired().size(),
        "AccountRegistered must NOT be fired on weak-password failure");
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

  // ---- Minimal fake CDI Event for unit tests ----

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

    /** Returns an unmodifiable snapshot of all events fired so far. */
    List<T> fired() {
      return List.copyOf(firedEvents);
    }

    /** Resets the captured list (call between sub-scenarios in one test). */
    void clear() {
      firedEvents.clear();
    }
  }
}
