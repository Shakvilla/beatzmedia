package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.AccountAdminView;
import org.shakvilla.beatzmedia.identity.application.service.SuspendAccountService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountAlreadySuspendedException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountStatus;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

/**
 * Unit tests for {@link SuspendAccountService}. Covers LLFR-ADMIN-02.3's identity-side domain
 * mutation: suspend, already-suspended guard, not-found.
 */
@Tag("unit")
class SuspendAccountServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");

  private FakeAccountRepository repo;
  private SuspendAccountService service;

  @BeforeEach
  void setUp() {
    repo = new FakeAccountRepository();
    service = new SuspendAccountService(repo, FakeClock.at(NOW));
  }

  @Test
  void suspend_active_account_sets_status_suspended() {
    AccountId id = new AccountId("acc-1");
    repo.seed(activeAccount(id));

    AccountAdminView view = service.suspend(id);

    assertEquals("suspended", view.status());
    assertEquals(AccountStatus.suspended, repo.findById(id).orElseThrow().getStatus());
  }

  @Test
  void suspend_already_suspended_throws_AccountAlreadySuspendedException() {
    AccountId id = new AccountId("acc-2");
    Account account = activeAccount(id);
    account.suspend(NOW);
    repo.seed(account);

    assertThrows(AccountAlreadySuspendedException.class, () -> service.suspend(id));
  }

  @Test
  void suspend_unknown_account_throws_AccountNotFoundException() {
    assertThrows(AccountNotFoundException.class, () -> service.suspend(new AccountId("no-such")));
  }

  private static Account activeAccount(AccountId id) {
    return Account.createFan(id, "Test User", id.value() + "@example.com",
        new Credential(id, "hash"), NOW);
  }
}
