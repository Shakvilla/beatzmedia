package org.shakvilla.beatzmedia.studio.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.in.GetOwnedEpisodeIds;
import org.shakvilla.beatzmedia.studio.application.port.out.OwnershipReader;

/**
 * Implements studio's {@link OwnershipReader} output port by calling commerce's {@link
 * GetOwnedEpisodeIds} INPUT port in-process — studio never reads commerce's {@code
 * ownership_grant} table directly. Mirrors {@code podcasts.adapter.out.integration
 * .OwnershipReaderAdapter} (WU-POD-1). Studio ADD §5.2 (WU-STU-2 addition).
 */
@ApplicationScoped
public class OwnershipReaderAdapter implements OwnershipReader {

  private final GetOwnedEpisodeIds getOwnedEpisodeIds;

  @Inject
  public OwnershipReaderAdapter(GetOwnedEpisodeIds getOwnedEpisodeIds) {
    this.getOwnedEpisodeIds = getOwnedEpisodeIds;
  }

  @Override
  public boolean hasAnyOwner(String episodeId) {
    return getOwnedEpisodeIds.hasAnyOwner(episodeId);
  }
}
