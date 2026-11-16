package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.out.AuthSessionRepository;
import org.shakvilla.beatzmedia.identity.application.service.BrowserSessionService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountStatus;
import org.shakvilla.beatzmedia.identity.domain.AuthSession;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.identity.fakes.FakeTokenIssuer;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

@Tag("unit")
class BrowserSessionServiceTest {
  private static final AccountId ACCOUNT_ID = new AccountId("account-1");
  private final MemorySessions sessions = new MemorySessions();
  private final FakeAccountRepository accounts = new FakeAccountRepository();
  private final FakeClock clock = FakeClock.at("2026-11-13T10:00:00Z");
  private BrowserSessionService service;

  @BeforeEach
  void setUp() {
    accounts.save(Account.createSocialFan(ACCOUNT_ID, "Alice", "alice@example.com", null, clock.now()));
    service = new BrowserSessionService(
        sessions, accounts, new FakeTokenIssuer(), FakeIds.sequential("session"),
        clock, 3600, 2_592_000);
  }

  @Test
  void startStoresOnlyHashAndRenewReturnsFreshAccount() {
    String cookie = service.start(ACCOUNT_ID);
    AuthSession stored = sessions.rows.get("session-1");
    assertNotEquals(cookie, stored.tokenHash());
    assertEquals(64, stored.tokenHash().length());

    clock.advanceSeconds(120);
    var result = service.renew(cookie).orElseThrow();
    assertEquals("alice@example.com", result.account().email());
    assertTrue(result.token().contains("fan"));
    assertEquals(clock.now(), sessions.rows.get("session-1").lastUsedAt());
  }

  @Test
  void idleAndAbsoluteLimitsAreEnforcedByServer() {
    String cookie = service.start(ACCOUNT_ID);
    clock.advanceSeconds(3600);
    assertTrue(service.renew(cookie).isEmpty());
    assertFalse(sessions.rows.get("session-1").activeAt(clock.now(), java.time.Duration.ofHours(1)));

    String second = service.start(ACCOUNT_ID);
    clock.advanceSeconds(2_592_000);
    AuthSession row = sessions.rows.get("session-2");
    sessions.save(row.usedAt(clock.now().minusSeconds(1)));
    assertTrue(service.renew(second).isEmpty());
  }

  @Test
  void logoutRevokesSessionAndWrongSecretCannotRevokeIt() {
    String cookie = service.start(ACCOUNT_ID);
    assertTrue(service.renew("session-1.wrong").isEmpty());
    assertTrue(service.renew(cookie).isPresent());
    service.revoke("session-1.wrong");
    assertTrue(service.renew(cookie).isPresent());
    service.revoke(cookie);
    assertTrue(service.renew(cookie).isEmpty());
  }

  @Test
  void suspendedAccountCannotRenew() {
    String cookie = service.start(ACCOUNT_ID);
    accounts.save(Account.reconstitute(
        ACCOUNT_ID, "Alice", "alice@example.com", null, false, false, false,
        AccountStatus.suspended, Instant.parse("2026-11-13T10:00:00Z"), clock.now(), null));
    assertTrue(service.renew(cookie).isEmpty());
  }

  private static class MemorySessions implements AuthSessionRepository {
    private final Map<String, AuthSession> rows = new HashMap<>();

    @Override
    public void save(AuthSession session) {
      rows.put(session.id(), session);
    }

    @Override
    public Optional<AuthSession> findForUpdate(String id) {
      return Optional.ofNullable(rows.get(id));
    }
  }
}
