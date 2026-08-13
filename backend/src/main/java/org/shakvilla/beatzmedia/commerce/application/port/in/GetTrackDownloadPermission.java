package org.shakvilla.beatzmedia.commerce.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: does this account hold an active ownership grant for this track that permits
 * downloading the audio?
 *
 * <p>Answered from {@code ownership_grant.downloadable} — the permission captured onto the grant at
 * settlement — and never from {@code release.downloadable}. Commerce owns a
 * {@code CatalogExpansionReader.isDownloadable} port that reads the release, but that is the WRITE
 * path: it decides what a NEW grant is stamped with. Answering this read from it would retract
 * downloads from people who already paid, the moment an artist changed their mind.
 *
 * <p>Consumed in-process by the playback module's {@code DownloadPermissionReader} output port —
 * playback never reads commerce's {@code ownership_grant} table directly. Mirrors the precedent set
 * by {@link GetOwnedEpisodeIds} for podcasts. Commerce ADD §4.1.
 */
public interface GetTrackDownloadPermission {

  /** {@code true} only when an ACTIVE grant exists for the track AND that grant permits download. */
  boolean mayDownload(AccountId account, String trackId);
}
