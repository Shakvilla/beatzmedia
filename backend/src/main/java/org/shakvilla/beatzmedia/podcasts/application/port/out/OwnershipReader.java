package org.shakvilla.beatzmedia.podcasts.application.port.out;

import java.util.Set;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;

/**
 * Output port: per-caller episode ownership lookup. Adapter calls the owning module's (commerce)
 * INPUT port in-process — podcasts never reads commerce/library tables directly. ADD §4.2.
 */
public interface OwnershipReader {

  boolean ownsEpisode(AccountId caller, EpisodeId episode);

  /** Batched lookup to avoid N+1 when decorating an episode list. */
  Set<EpisodeId> ownedEpisodes(AccountId caller, Set<EpisodeId> candidates);
}
