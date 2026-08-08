package org.shakvilla.beatzmedia.catalog.application.service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.catalog.application.port.in.ProjectReleaseAlbum;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.CatalogDefaults;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Projects a live release into the fan-facing {@code album} row. See {@link ProjectReleaseAlbum}
 * for why nothing did this before.
 *
 * <p><strong>Every release type gets an album, including singles.</strong> The album row is what
 * the fan-facing collection surfaces read; restricting it to {@code album}/{@code ep} would leave a
 * single invisible in "New releases", which is the common case for this catalogue and precisely the
 * symptom that surfaced the gap. Streaming services model a single as a one-track album for the
 * same reason.
 *
 * <p><strong>The album shares the release's id.</strong> The relationship is one-to-one and the id
 * is already unique, so reusing it makes the projection naturally idempotent — no lookup table, and
 * a replayed event overwrites rather than duplicates.
 */
@ApplicationScoped
public class ProjectReleaseAlbumService implements ProjectReleaseAlbum {

  private static final Logger LOG = Logger.getLogger(ProjectReleaseAlbumService.class);

  private final CatalogRepository catalog;
  private final Clock clock;

  @Inject
  public ProjectReleaseAlbumService(CatalogRepository catalog, Clock clock) {
    this.catalog = catalog;
    this.clock = clock;
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void project(String releaseId) {
    Release release = catalog.findRelease(new ReleaseId(releaseId)).orElse(null);
    if (release == null) {
      LOG.warnf("album projection: release %s no longer exists", releaseId);
      return;
    }

    // Only a live release belongs in the fan-facing catalogue. Guarding here rather than trusting
    // the caller means a stale or replayed event cannot resurrect a release that has since come
    // down.
    if (release.getStatus() != ReleaseStatus.live) {
      LOG.infof(
          "album projection: release %s is %s, not live — removing any existing album",
          releaseId, release.getStatus());
      remove(releaseId);
      return;
    }

    ArtistId artistId = new ArtistId(release.getArtistId());
    String artistName =
        catalog.findArtist(artistId).map(ArtistProfile::getName).orElse(null);
    if (artistName == null) {
      // album.artist_id is a foreign key to artist_profile — without the artist there is nothing
      // valid to write, and inventing a name would put a fabricated credit on a fan-facing page.
      LOG.warnf(
          "album projection: artist %s for release %s has no profile — skipping",
          release.getArtistId(), releaseId);
      return;
    }

    List<String> trackIds =
        release.getTracks().stream()
            .sorted(java.util.Comparator.comparingInt(ReleaseTrack::position))
            .map(ReleaseTrack::trackId)
            .toList();

    Album album =
        new Album(
            new AlbumId(releaseId),
            release.getTitle(),
            artistId,
            artistName,
            year(release),
            // album.cover_image is NOT NULL and a release may still be waiting on artwork.
            Optional.ofNullable(release.getCoverImage())
                .filter(s -> !s.isBlank())
                .orElse(CatalogDefaults.PLACEHOLDER_IMAGE),
            Optional.ofNullable(release.getGenre())
                .filter(s -> !s.isBlank())
                .map(List::of)
                .orElseGet(List::of),
            trackIds,
            release.getListPriceMinor());

    catalog.saveAlbum(album);
    LOG.infof("album projection: release %s projected with %d track(s)", releaseId, trackIds.size());
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void remove(String releaseId) {
    catalog.deleteAlbum(new AlbumId(releaseId));
  }

  /**
   * The year the release reached fans, falling back to today for a release that is live without a
   * recorded go-live time. {@code newestAlbums()} orders by this column, so a zero would bury a new
   * release at the bottom of "New releases".
   */
  private int year(Release release) {
    return Optional.ofNullable(release.getWentLiveAt())
        .orElseGet(clock::now)
        .atZone(ZoneOffset.UTC)
        .getYear();
  }
}
