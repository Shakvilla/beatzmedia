package org.shakvilla.beatzmedia.playback.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: return a signed, time-boxed URL to the LOSSLESS (FLAC) file for a track the caller
 * owns and is permitted to download.
 *
 * <p>Trigger: {@code GET /v1/tracks/:id/download}. Auth: required — unlike the streaming endpoint
 * there is no anonymous case, so the caller is an {@link AccountId}, not an {@code Optional}.
 *
 * <p>Guard order is load-bearing and each failure is distinct:
 *
 * <ol>
 *   <li>track unknown → {@code TrackNotFoundException} (404 TRACK_NOT_FOUND)
 *   <li>not owned → {@code NotOwnedException} (403 NOT_OWNED)
 *   <li>owned, but the caller's own GRANT forbids downloading →
 *       {@code DownloadNotAllowedException} (409 DOWNLOAD_NOT_ALLOWED)
 *   <li>permitted, but no FLAC rendition exists yet → {@code DownloadNotReadyException} (409
 *       DOWNLOAD_NOT_READY)
 * </ol>
 *
 * <p>Ownership precedes permission so that a stranger's answer never varies with the release's
 * download setting or the asset's transcode state.
 */
public interface GetDownloadUrl {

  DownloadUrlResult getDownloadUrl(TrackId track, AccountId caller);
}
