package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.AccountView;
import org.shakvilla.beatzmedia.identity.application.service.GetCurrentAccountService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;

/**
 * Unit tests for {@link GetCurrentAccountService}. Covers LLFR-IDENTITY-02.1 acceptance criteria
 * (identity ADD §11).
 */
@Tag("unit")
class GetCurrentAccountServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");

  private FakeAccountRepository repo;
  private GetCurrentAccountService service;

  @BeforeEach
  void setUp() {
    repo = new FakeAccountRepository();
    service = new GetCurrentAccountService(repo);
  }

  @Test
  void current_returns_account_view_matching_contract_shape() {
    AccountId id = new AccountId("acc-1");
    Credential cred = new Credential(id, "HASHED:pw");
    repo.seed(Account.createFan(id, "Alice", "alice@example.com", cred, NOW));

    AccountView view = service.current(id);

    assertEquals("acc-1", view.id());
    assertEquals("Alice", view.name());
    assertEquals("alice@example.com", view.email());
    assertEquals(false, view.isArtist());
    assertEquals(false, view.isAdmin());
  }

  @Test
  void current_unknown_account_throws_AccountNotFoundException() {
    assertThrows(AccountNotFoundException.class, () -> service.current(new AccountId("missing")));
  }
}
