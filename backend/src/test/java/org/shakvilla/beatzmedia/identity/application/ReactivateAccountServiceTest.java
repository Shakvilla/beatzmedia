package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.AccountAdminView;
import org.shakvilla.beatzmedia.identity.application.service.ReactivateAccountService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountNotSuspendedException;
import org.shakvilla.beatzmedia.identity.domain.AccountStatus;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

/**
 * Unit tests for {@link ReactivateAccountService}. Covers LLFR-ADMIN-02.4's identity-side domain
 * mutation: reactivate, not-suspended guard, not-found.
 */
@Tag("unit")
class ReactivateAccountServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");

  private FakeAccountRepository repo;
  private ReactivateAccountService service;

  @BeforeEach
  void setUp() {
    repo = new FakeAccountRepository();
    service = new ReactivateAccountService(repo, FakeClock.at(NOW));
  }

  @Test
  void reactivate_suspended_account_sets_status_active() {
    AccountId id = new AccountId("acc-1");
    Account account = Account.createFan(id, "Test User", "acc-1@example.com",
        new Credential(id, "hash"), NOW);
    account.suspend(NOW);
    repo.seed(account);

    AccountAdminView view = service.reactivate(id);

    assertEquals("active", view.status());
    assertEquals(AccountStatus.active, repo.findById(id).orElseThrow().getStatus());
  }

  @Test
  void reactivate_not_suspended_throws_AccountNotSuspendedException() {
    AccountId id = new AccountId("acc-2");
    repo.seed(Account.createFan(id, "Test User", "acc-2@example.com",
        new Credential(id, "hash"), NOW));

    assertThrows(AccountNotSuspendedException.class, () -> service.reactivate(id));
  }

  @Test
  void reactivate_unknown_account_throws_AccountNotFoundException() {
    assertThrows(AccountNotFoundException.class,
        () -> service.reactivate(new AccountId("no-such")));
  }
}
