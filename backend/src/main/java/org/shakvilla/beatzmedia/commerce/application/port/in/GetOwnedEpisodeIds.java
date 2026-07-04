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
}
