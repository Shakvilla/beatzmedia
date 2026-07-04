package org.shakvilla.beatzmedia.playback.application.port.out;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Output port: does the given account currently own the given track? Adapter calls the owning
 * module's INPUT port in-process (library's {@code GetOwnedTrackIds}, itself backed by commerce's
 * ownership grants from WU-COM-2) — playback never reads commerce/library tables directly.
 * Playback ADD §4.2.
 */
public interface OwnershipReader {

  boolean isOwned(AccountId account, TrackId track);
}
