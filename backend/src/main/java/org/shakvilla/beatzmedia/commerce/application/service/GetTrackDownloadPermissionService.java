package org.shakvilla.beatzmedia.commerce.application.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.commerce.application.port.in.GetTrackDownloadPermission;
import org.shakvilla.beatzmedia.commerce.application.port.out.OwnershipRepository;
import org.shakvilla.beatzmedia.commerce.domain.OwnershipGrant;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Application service for {@link GetTrackDownloadPermission}, consumed in-process by the playback
 * module's {@code DownloadPermissionReaderAdapter}. Commerce ADD §4.1.
 */
@ApplicationScoped
@Transactional
public class GetTrackDownloadPermissionService implements GetTrackDownloadPermission {

  private final OwnershipRepository ownershipRepository;

  @Inject
  public GetTrackDownloadPermissionService(OwnershipRepository ownershipRepository) {
    this.ownershipRepository = ownershipRepository;
  }

  @Override
  public boolean mayDownload(AccountId account, String trackId) {
    // The grant is the authority. Deliberately NOT CatalogExpansionReader.isDownloadable(...):
    // that reads release.downloadable, which is the artist's setting for FUTURE sales. Two tracks
    // of the same release bought either side of an artist changing their mind must give different
    // answers here, and only the per-grant value can do that.
    return ownershipRepository
        .findActiveForTrack(account, trackId)
        .map(OwnershipGrant::isDownloadable)
        // No active grant (never bought, or refunded — INV-9): no download. Fail closed.
        .orElse(false);
  }

  @Override
  public Set<String> downloadableTrackIds(AccountId account, Collection<String> trackIds) {
    if (trackIds == null || trackIds.isEmpty()) {
      return Set.of();
    }
    return Set.copyOf(ownershipRepository.downloadableTrackIds(account, List.copyOf(trackIds)));
  }
}
