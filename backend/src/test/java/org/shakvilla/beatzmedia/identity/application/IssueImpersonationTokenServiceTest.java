package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.ImpersonationTokenView;
import org.shakvilla.beatzmedia.identity.application.service.IssueImpersonationTokenService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.identity.fakes.FakeTokenIssuer;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

/**
 * Unit tests for {@link IssueImpersonationTokenService}. Covers LLFR-ADMIN-02.5's identity-side
 * token issuance: scoped roles (fan + artist if applicable, NEVER an admin role even if the
 * target is an admin member), a real signed token via {@link
 * org.shakvilla.beatzmedia.identity.application.port.out.TokenIssuer}, and not-found.
 */
@Tag("unit")
class IssueImpersonationTokenServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");
  private static final long IMPERSONATION_TTL_SECONDS = 900;

  private FakeAccountRepository repo;
  private FakeTokenIssuer tokenIssuer;
  private IssueImpersonationTokenService service;

  @BeforeEach
  void setUp() {
    repo = new FakeAccountRepository();
    tokenIssuer = new FakeTokenIssuer();
    service = new IssueImpersonationTokenService(
        repo, tokenIssuer, FakeClock.at(NOW), IMPERSONATION_TTL_SECONDS);
  }

  private static final AccountId ACTOR = new AccountId("acc-admin");

  @Test
  void issue_for_fan_returns_fan_scope_only() {
    AccountId id = new AccountId("acc-1");
    repo.seed(Account.createFan(id, "Fan User", "acc-1@example.com",
        new Credential(id, "hash"), NOW));

    ImpersonationTokenView view = service.issue(ACTOR, id);

    assertEquals(java.util.Set.of("fan"), view.scopes());
    assertEquals(NOW.plusSeconds(IMPERSONATION_TTL_SECONDS), view.expiresAt());
    assertTrue(view.token().contains(id.value()));
  }

  @Test
  void issue_for_artist_includes_artist_scope() {
    AccountId id = new AccountId("acc-2");
    Account artist = Account.createFan(id, "Artist User", "acc-2@example.com",
        new Credential(id, "hash"), NOW).upgradeToArtist(NOW);
    repo.seed(artist);

    ImpersonationTokenView view = service.issue(ACTOR, id);

    assertEquals(java.util.Set.of("fan", "artist"), view.scopes());
  }

  @Test
  void issue_for_admin_account_never_includes_admin_role() {
    // Reconstitute an account with isAdmin=true — impersonation must still exclude any admin
    // scope even though the target happens to be an admin member (deliberate security default).
    AccountId id = new AccountId("acc-3");
    Account adminAccount = Account.reconstitute(
        id, "Admin User", "acc-3@example.com", null, false, true, false,
        org.shakvilla.beatzmedia.identity.domain.AccountStatus.active, NOW, NOW, null);
    repo.seed(adminAccount);

    ImpersonationTokenView view = service.issue(ACTOR, id);

    assertEquals(java.util.Set.of("fan"), view.scopes());
    assertFalse(view.scopes().stream().anyMatch(s -> s.contains("admin")),
        "impersonation must never grant an admin scope, even for an admin target");
  }

  @Test
  void issue_threads_the_real_actor_into_token_issuance() {
    // Confirms the actor id reaches TokenIssuer (security-review fix: the `act` claim source).
    AccountId id = new AccountId("acc-4");
    repo.seed(Account.createFan(id, "Fan User", "acc-4@example.com",
        new Credential(id, "hash"), NOW));

    ImpersonationTokenView view = service.issue(ACTOR, id);

    assertTrue(view.token().contains("act=" + ACTOR.value()),
        "the real admin actor must reach TokenIssuer#issueImpersonation for the `act` claim");
  }

  @Test
  void issue_unknown_account_throws_AccountNotFoundException() {
    assertThrows(AccountNotFoundException.class,
        () -> service.issue(ACTOR, new AccountId("no-such")));
  }
}
