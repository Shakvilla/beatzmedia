package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.AccountView;
import org.shakvilla.beatzmedia.identity.application.service.UpgradeToArtistService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.platform.domain.FeatureDisabledException;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;
import org.shakvilla.beatzmedia.platform.fakes.FakeFeatureFlags;

/**
 * Unit tests for {@link UpgradeToArtistService}. Uses fakes for all output ports. Testing strategy
 * §2 / Identity ADD §11 acceptance case 02.2.
 */
class UpgradeToArtistServiceTest {

  private FakeAccountRepository accountRepository;
  private FakeFeatureFlags featureFlags;
  private FakeClock clock;
  private FakeEventBus<org.shakvilla.beatzmedia.identity.domain.ArtistUpgraded> eventBus;
  private UpgradeToArtistService service;

  @BeforeEach
  void setUp() {
    accountRepository = new FakeAccountRepository();
    featureFlags = new FakeFeatureFlags();
    clock = new FakeClock(Instant.parse("2026-01-01T00:00:00Z"));
    eventBus = new FakeEventBus<>();
    service = new UpgradeToArtistService(accountRepository, featureFlags, clock, eventBus);
  }

  @Test
  void upgrade_flips_isArtist_and_publishes_event() {
    AccountId id = new AccountId("acc-1");
    accountRepository.seed(fanAccount(id));

    AccountView result = service.upgrade(id);

    assertTrue(result.isArtist(), "isArtist must be true after upgrade");
    assertEquals(1, eventBus.fired.size(), "ArtistUpgraded event must be published");
    assertEquals("acc-1", eventBus.fired.get(0).accountId());
  }

  @Test
  void upgrade_idempotent_second_call_is_noop_success() {
    AccountId id = new AccountId("acc-2");
    accountRepository.seed(fanAccount(id));

    service.upgrade(id); // first call
    AccountView result = service.upgrade(id); // second call

    assertTrue(result.isArtist());
    // Event must be published only once (idempotency: second call is a no-op)
    assertEquals(1, eventBus.fired.size(), "Event must be published only once");
  }

  @Test
  void upgrade_already_artist_returns_success_without_republishing() {
    AccountId id = new AccountId("acc-3");
    Account alreadyArtist = Account.reconstitute(id, "Art", "art@x.com", null,
        true, false, org.shakvilla.beatzmedia.identity.domain.AccountStatus.active,
        Instant.now(), Instant.now(), null);
    accountRepository.seed(alreadyArtist);

    AccountView result = service.upgrade(id);

    assertTrue(result.isArtist());
    assertEquals(0, eventBus.fired.size(), "Event must NOT be published again");
  }

  @Test
  void upgrade_feature_disabled_throws_FeatureDisabledException() {
    featureFlags.disable(FeatureKey.ARTIST_SIGNUPS);
    AccountId id = new AccountId("acc-4");
    accountRepository.seed(fanAccount(id));

    assertThrows(FeatureDisabledException.class, () -> service.upgrade(id));
  }

  @Test
  void upgrade_unknown_account_throws_AccountNotFoundException() {
    AccountId unknownId = new AccountId("no-such-account");
    assertThrows(AccountNotFoundException.class, () -> service.upgrade(unknownId));
  }

  // ---- helpers ----

  private static Account fanAccount(AccountId id) {
    return Account.createFan(id, "Fan User", id.value() + "@example.com",
        new Credential(id, "hash"), Instant.now());
  }

  /** Minimal clock fake. */
  static class FakeClock implements org.shakvilla.beatzmedia.platform.application.port.out.Clock {
    private final Instant now;

    FakeClock(Instant now) {
      this.now = now;
    }

    @Override
    public Instant now() {
      return now;
    }

    @Override
    public java.time.LocalDate today(java.time.ZoneId zone) {
      return now.atZone(zone).toLocalDate();
    }
  }

  /** Minimal CDI Event fake — collects fired events. */
  static class FakeEventBus<T> implements jakarta.enterprise.event.Event<T> {
    final java.util.List<T> fired = new java.util.ArrayList<>();

    @Override
    public void fire(T event) {
      fired.add(event);
    }

    @Override
    public <U extends T> java.util.concurrent.CompletionStage<U> fireAsync(U event) {
      fired.add(event);
      return java.util.concurrent.CompletableFuture.completedFuture(event);
    }

    @Override
    public <U extends T> java.util.concurrent.CompletionStage<U> fireAsync(U event,
        jakarta.enterprise.event.NotificationOptions options) {
      return fireAsync(event);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <U extends T> jakarta.enterprise.event.Event<U> select(
        jakarta.enterprise.util.TypeLiteral<U> subtype,
        java.lang.annotation.Annotation... qualifiers) {
      return (jakarta.enterprise.event.Event<U>) this;
    }

    @Override
    public <U extends T> jakarta.enterprise.event.Event<U> select(Class<U> subtype,
        java.lang.annotation.Annotation... qualifiers) {
      return new FakeEventBus<>();
    }

    @Override
    public jakarta.enterprise.event.Event<T> select(java.lang.annotation.Annotation... qualifiers) {
      return this;
    }
  }
}
