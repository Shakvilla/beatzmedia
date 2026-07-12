package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.AccountAdminView;
import org.shakvilla.beatzmedia.identity.application.service.BanAccountService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountStatus;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

/**
 * Unit tests for {@link BanAccountService} (LLFR-ADMIN-07.1's identity-side mutation): ban an
 * account (→ banned, cannot authenticate), idempotent re-ban, not-found.
 */
@Tag("unit")
class BanAccountServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

  private FakeAccountRepository repo;
  private BanAccountService service;

  @BeforeEach
  void setUp() {
    repo = new FakeAccountRepository();
    service = new BanAccountService(repo, FakeClock.at(NOW));
  }

  @Test
  void ban_sets_status_banned_and_blocks_authentication() {
    AccountId id = new AccountId("acc-1");
    repo.seed(activeAccount(id));

    AccountAdminView view = service.ban(id);

    assertEquals("banned", view.status());
    Account saved = repo.findById(id).orElseThrow();
    assertEquals(AccountStatus.banned, saved.getStatus());
    assertFalse(saved.canAuthenticate());
  }

  @Test
  void ban_is_idempotent_on_an_already_banned_account() {
    AccountId id = new AccountId("acc-2");
    Account account = activeAccount(id);
    account.ban(NOW);
    repo.seed(account);

    AccountAdminView view = service.ban(id); // no 409
    assertEquals("banned", view.status());
  }

  @Test
  void ban_unknown_account_throws_AccountNotFoundException() {
    assertThrows(AccountNotFoundException.class, () -> service.ban(new AccountId("no-such")));
  }

  private static Account activeAccount(AccountId id) {
    return Account.createFan(
        id, "Test User", id.value() + "@example.com", new Credential(id, "hash"), NOW);
  }
}
