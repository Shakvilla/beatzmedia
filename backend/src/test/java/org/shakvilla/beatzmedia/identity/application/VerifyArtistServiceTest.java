package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.AccountAdminView;
import org.shakvilla.beatzmedia.identity.application.service.VerifyArtistService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountAlreadyVerifiedException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

/**
 * Unit tests for {@link VerifyArtistService}. Covers LLFR-ADMIN-02.2's identity-side domain
 * mutation: verify, already-verified guard, not-found. No "must be an artist" guard by design (a
 * verified fan is a harmless no-op-ish state — see admin ADD WU-ADM-2 as-built notes).
 */
@Tag("unit")
class VerifyArtistServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");

  private FakeAccountRepository repo;
  private VerifyArtistService service;

  @BeforeEach
  void setUp() {
    repo = new FakeAccountRepository();
    service = new VerifyArtistService(repo, FakeClock.at(NOW));
  }

  @Test
  void verify_unverified_account_sets_verified_true() {
    AccountId id = new AccountId("acc-1");
    repo.seed(Account.createFan(id, "Test User", "acc-1@example.com",
        new Credential(id, "hash"), NOW));

    AccountAdminView view = service.verify(id);

    assertTrue(view.verified());
    assertTrue(repo.findById(id).orElseThrow().isVerified());
  }

  @Test
  void verify_already_verified_throws_AccountAlreadyVerifiedException() {
    AccountId id = new AccountId("acc-2");
    Account account = Account.createFan(id, "Test User", "acc-2@example.com",
        new Credential(id, "hash"), NOW);
    account.verifyArtist(NOW);
    repo.seed(account);

    assertThrows(AccountAlreadyVerifiedException.class, () -> service.verify(id));
  }

  @Test
  void verify_unknown_account_throws_AccountNotFoundException() {
    assertThrows(AccountNotFoundException.class, () -> service.verify(new AccountId("no-such")));
  }

  @Test
  void verify_fan_account_succeeds_no_artist_guard() {
    AccountId id = new AccountId("acc-3");
    repo.seed(Account.createFan(id, "Fan User", "acc-3@example.com",
        new Credential(id, "hash"), NOW));

    AccountAdminView view = service.verify(id);

    assertTrue(view.verified(), "no artist-only guard — verifying a fan is a harmless no-op-ish state");
  }
}
