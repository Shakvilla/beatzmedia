package org.shakvilla.beatzmedia.playback.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.GetOwnedTrackIds;
import org.shakvilla.beatzmedia.playback.application.port.out.OwnershipReader;

/**
 * Implements playback's {@link OwnershipReader} output port by calling library's
 * {@link GetOwnedTrackIds} INPUT port in-process (itself backed by commerce's ownership grants,
 * WU-COM-2) — playback never reads commerce/library tables directly. Playback ADD §5.2.
 */
@ApplicationScoped
public class OwnershipReaderAdapter implements OwnershipReader {

  private final GetOwnedTrackIds getOwnedTrackIds;

  @Inject
  public OwnershipReaderAdapter(GetOwnedTrackIds getOwnedTrackIds) {
    this.getOwnedTrackIds = getOwnedTrackIds;
  }

  @Override
  public boolean isOwned(AccountId account, TrackId track) {
    return getOwnedTrackIds.ownedTrackIds(account).contains(track.value());
  }
}
