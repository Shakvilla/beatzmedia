package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
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
  private FakeAuditWriter auditWriter;
  private FakeEventBus<org.shakvilla.beatzmedia.identity.domain.ArtistUpgraded> eventBus;
  private UpgradeToArtistService service;

  @BeforeEach
  void setUp() {
    accountRepository = new FakeAccountRepository();
    featureFlags = new FakeFeatureFlags();
    clock = new FakeClock(Instant.parse("2026-01-01T00:00:00Z"));
    auditWriter = new FakeAuditWriter();
    eventBus = new FakeEventBus<>();
    org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator idGenerator =
        new org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator() {
          @Override public String newId() { return UUID.randomUUID().toString(); }
          @Override public String newOrderRef(int year) { return "BZ-" + year + "-00001"; }
        };
    service = new UpgradeToArtistService(
        accountRepository, featureFlags, clock, idGenerator, auditWriter, eventBus);
  }

  @Test
  void upgrade_flips_isArtist_and_publishes_event() {
    AccountId id = new AccountId("acc-1");
    accountRepository.seed(fanAccount(id));

    AccountView result = service.upgrade(id);

    assertTrue(result.isArtist(), "isArtist must be true after upgrade");
    assertEquals(1, eventBus.fired.size(), "ArtistUpgraded event must be published");
    // Event carries the identity attributes the catalog reactor needs to seed artist_profile.
    org.shakvilla.beatzmedia.identity.domain.ArtistUpgraded published = eventBus.fired.get(0);
    assertEquals("acc-1", published.accountId());
    assertEquals("Fan User", published.name());
    assertEquals("acc-1@example.com", published.email());
    // INV-10: audit entry must be recorded on upgrade
    assertEquals(1, auditWriter.size(), "AuditEntry must be appended on BECOME_ARTIST");
    AuditEntry audit = auditWriter.all().get(0);
    assertEquals("BECOME_ARTIST", audit.getAction());
    assertEquals("acc-1", audit.getActor());
  }

  @Test
  void upgrade_idempotent_second_call_is_noop_success() {
    AccountId id = new AccountId("acc-2");
    accountRepository.seed(fanAccount(id));

    service.upgrade(id); // first call
    AccountView result = service.upgrade(id); // second call

    assertTrue(result.isArtist());
    // Event and audit must each be recorded only once (idempotency: second call is no-op)
    assertEquals(1, eventBus.fired.size(), "Event must be published only once");
    assertEquals(1, auditWriter.size(), "AuditEntry must be appended only once");
  }

  @Test
  void upgrade_already_artist_returns_success_without_republishing() {
    AccountId id = new AccountId("acc-3");
    Account alreadyArtist = Account.reconstitute(id, "Art", "art@x.com", null,
        true, false, false, org.shakvilla.beatzmedia.identity.domain.AccountStatus.active,
        Instant.now(), Instant.now(), null);
    accountRepository.seed(alreadyArtist);

    AccountView result = service.upgrade(id);

    assertTrue(result.isArtist());
    assertEquals(0, eventBus.fired.size(), "Event must NOT be published again");
    assertEquals(0, auditWriter.size(), "AuditEntry must NOT be appended for idempotent no-op");
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
