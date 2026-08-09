package org.shakvilla.beatzmedia.audit.adapter.out.identity;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.audit.application.port.out.ActorDirectory;
import org.shakvilla.beatzmedia.identity.application.port.in.AccountView;
import org.shakvilla.beatzmedia.identity.application.port.in.GetCurrentAccount;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Resolves actor names through identity's input port.
 *
 * <p>audit → identity by input port, never by table: audit owns no account data and does not read
 * another module's schema.
 *
 * <p><strong>Never throws.</strong> This runs inside the transaction of the mutation being audited.
 * An actor id that resolves to nothing is entirely normal — the scheduler, a payment webhook, and a
 * manual database bootstrap all write audit rows without being user accounts — and none of those
 * should cost the caller its transaction. An unnamed row is a small loss; a lost mutation is not.
 */
@ApplicationScoped
public class IdentityActorDirectory implements ActorDirectory {

  private static final Logger LOG = Logger.getLogger(IdentityActorDirectory.class);

  private final GetCurrentAccount accounts;

  @Inject
  public IdentityActorDirectory(GetCurrentAccount accounts) {
    this.accounts = accounts;
  }

  @Override
  public Optional<String> displayName(String actorId) {
    if (actorId == null || actorId.isBlank()) {
      return Optional.empty();
    }
    try {
      AccountView account = accounts.current(new AccountId(actorId));
      return Optional.ofNullable(account.name()).filter(n -> !n.isBlank());
    } catch (RuntimeException e) {
      // Unknown or non-account actors are expected, so this is debug rather than warn — it would
      // otherwise fire on every scheduled job that audits.
      LOG.debugf("audit: no display name for actor %s (%s)", actorId, e.getClass().getSimpleName());
      return Optional.empty();
    }
  }
}
