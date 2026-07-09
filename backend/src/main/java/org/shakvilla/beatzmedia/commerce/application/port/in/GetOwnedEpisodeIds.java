package org.shakvilla.beatzmedia.commerce.application.port.in;

import java.util.List;
import java.util.Set;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: does the caller own the given podcast episode, and — batched — which of a set of
 * candidate episode ids does the caller own? Backed by real {@code ownership_grant} rows created
 * on settlement (WU-COM-2/INV-1); episode ownership is never stored outside commerce. Consumed
 * in-process by the podcasts module's {@code OwnershipReader} output port (WU-POD-1) — podcasts
 * never reads commerce's {@code ownership_grant} table directly. Commerce ADD §4.1.
 */
public interface GetOwnedEpisodeIds {

  boolean isOwned(AccountId account, String episodeId);

  /** Of the given candidates, the subset the account currently owns (single batched query). */
  Set<String> ownedOf(AccountId account, List<String> candidateEpisodeIds);

  /**
   * Does ANY account currently own the given episode — an aggregate, not account-scoped, query.
   * Backs the {@code studio} module's delete guard (OQ-8 / WU-STU-2): a {@code published} episode
   * with at least one owner cannot be deleted. Added for WU-STU-2, mirroring the precedent set by
   * this same port's original addition for WU-POD-1's analogous cross-module read need.
   */
  boolean hasAnyOwner(String episodeId);
}
