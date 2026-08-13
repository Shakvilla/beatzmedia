package org.shakvilla.beatzmedia.playback.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.commerce.application.port.in.GetTrackDownloadPermission;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.application.port.out.DownloadPermissionReader;

/**
 * Implements playback's {@link DownloadPermissionReader} output port by calling commerce's
 * {@link GetTrackDownloadPermission} INPUT port in-process — playback never reads commerce's
 * {@code ownership_grant} table directly. Same shape as {@code OwnershipReaderAdapter}. Playback
 * ADD §5.2.
 *
 * <p>Commerce answers from the caller's own grant; nothing on this hop consults the release.
 */
@ApplicationScoped
public class DownloadPermissionReaderAdapter implements DownloadPermissionReader {

  private final GetTrackDownloadPermission getTrackDownloadPermission;

  @Inject
  public DownloadPermissionReaderAdapter(GetTrackDownloadPermission getTrackDownloadPermission) {
    this.getTrackDownloadPermission = getTrackDownloadPermission;
  }

  @Override
  public boolean mayDownload(AccountId account, TrackId track) {
    return getTrackDownloadPermission.mayDownload(account, track.value());
  }
}
