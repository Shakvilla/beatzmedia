package org.shakvilla.beatzmedia.library.adapter.out.integration;

import java.util.Collection;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.in.GetTrackDownloadPermission;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.out.DownloadPermissionReader;

/**
 * Implements library's {@link DownloadPermissionReader} output port by calling commerce's
 * {@link GetTrackDownloadPermission} INPUT port in-process — library never reads commerce's
 * {@code ownership_grant} table directly. Same shape as playback's
 * {@code DownloadPermissionReaderAdapter}. Library ADD §4.2.
 *
 * <p>Commerce answers from the caller's own grant; nothing on this hop consults the release, which
 * is exactly what lets the library collection view keep showing a download the buyer already paid
 * for even after the artist switches the release off.
 */
@ApplicationScoped
public class DownloadPermissionReaderAdapter implements DownloadPermissionReader {

  private final GetTrackDownloadPermission getTrackDownloadPermission;

  @Inject
  public DownloadPermissionReaderAdapter(GetTrackDownloadPermission getTrackDownloadPermission) {
    this.getTrackDownloadPermission = getTrackDownloadPermission;
  }

  @Override
  public Set<String> downloadableTrackIds(AccountId account, Collection<String> trackIds) {
    return getTrackDownloadPermission.downloadableTrackIds(account, trackIds);
  }
}
