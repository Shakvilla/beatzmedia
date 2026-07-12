package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.admin.application.port.out.AccountAdminPort;
import org.shakvilla.beatzmedia.identity.application.port.in.AccountAdminView;
import org.shakvilla.beatzmedia.identity.application.port.in.BanAccount;
import org.shakvilla.beatzmedia.identity.application.port.in.ImpersonationTokenView;
import org.shakvilla.beatzmedia.identity.application.port.in.IssueImpersonationToken;
import org.shakvilla.beatzmedia.identity.application.port.in.ReactivateAccount;
import org.shakvilla.beatzmedia.identity.application.port.in.SuspendAccount;
import org.shakvilla.beatzmedia.identity.application.port.in.VerifyArtist;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Implements admin's {@link AccountAdminPort} output port by calling {@code identity}'s
 * {@link SuspendAccount}, {@link ReactivateAccount}, {@link VerifyArtist}, and
 * {@link IssueImpersonationToken} INPUT ports in-process — {@code admin} never writes identity's
 * {@code account} table directly (the domain invariants live in {@code identity.domain.Account},
 * not duplicated here). Placed alongside {@link IdentityReaderAdapter} in {@code
 * adapter.out.persistence} — this module has no {@code adapter.out.integration} package yet
 * (same placement precedent as {@link AnalyticsAdminReaderAdapter}, admin ADD §4.3 / §13).
 */
@ApplicationScoped
public class AccountAdminPortAdapter implements AccountAdminPort {

  private final SuspendAccount suspendAccount;
  private final ReactivateAccount reactivateAccount;
  private final VerifyArtist verifyArtist;
  private final IssueImpersonationToken issueImpersonationToken;
  private final BanAccount banAccount;

  @Inject
  public AccountAdminPortAdapter(
      SuspendAccount suspendAccount,
      ReactivateAccount reactivateAccount,
      VerifyArtist verifyArtist,
      IssueImpersonationToken issueImpersonationToken,
      BanAccount banAccount) {
    this.suspendAccount = suspendAccount;
    this.reactivateAccount = reactivateAccount;
    this.verifyArtist = verifyArtist;
    this.issueImpersonationToken = issueImpersonationToken;
    this.banAccount = banAccount;
  }

  @Override
  public AccountMutationResult verifyArtist(String accountId) {
    return toResult(verifyArtist.verify(new AccountId(accountId)));
  }

  @Override
  public AccountMutationResult suspend(String accountId) {
    return toResult(suspendAccount.suspend(new AccountId(accountId)));
  }

  @Override
  public AccountMutationResult reactivate(String accountId) {
    return toResult(reactivateAccount.reactivate(new AccountId(accountId)));
  }

  @Override
  public AccountMutationResult ban(String accountId) {
    return toResult(banAccount.ban(new AccountId(accountId)));
  }

  @Override
  public ImpersonationResult issueImpersonationToken(String actorId, String accountId) {
    ImpersonationTokenView view =
        issueImpersonationToken.issue(new AccountId(actorId), new AccountId(accountId));
    return new ImpersonationResult(view.token(), view.expiresAt(), view.scopes());
  }

  private static AccountMutationResult toResult(AccountAdminView view) {
    return new AccountMutationResult(
        view.id(),
        view.name(),
        view.email(),
        view.isArtist(),
        view.verified(),
        view.status(),
        view.createdAt(),
        view.updatedAt());
  }
}
