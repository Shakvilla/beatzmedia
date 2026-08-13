package org.shakvilla.beatzmedia.playback.application.port.out;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Output port: may this account download this track?
 *
 * <p>Answered from the caller's own ownership GRANT, not from the release. The release's current
 * setting governs future sales only; reading it here would retract a download from someone who
 * already paid for it.
 *
 * <p>The adapter calls commerce's input port in-process — playback never reads commerce tables
 * (same rule as {@link OwnershipReader}). Playback ADD §4.2.
 */
public interface DownloadPermissionReader {

  /** {@code true} only when an active grant exists AND that grant permits downloading. */
  boolean mayDownload(AccountId account, TrackId track);
}
