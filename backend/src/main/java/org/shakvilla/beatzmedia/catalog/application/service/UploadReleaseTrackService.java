package org.shakvilla.beatzmedia.catalog.application.service;

import java.time.Instant;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.catalog.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.catalog.application.port.in.UploadReleaseTrack;
import org.shakvilla.beatzmedia.catalog.application.port.in.UploadedTrackView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.port.in.UploadOriginalUseCase;
import org.shakvilla.beatzmedia.media.domain.FileTooLargeException;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.media.domain.UnsupportedFormatException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;

/**
 * Application service for {@link UploadReleaseTrack}. Delegates the binary upload to the media
 * module's {@link UploadOriginalUseCase}, then creates a stub {@link Track} row with status
 * "uploading". LLFR-CATALOG-02.4.
 */
@ApplicationScoped
public class UploadReleaseTrackService implements UploadReleaseTrack {

  private static final Set<String> ALLOWED_TYPES = Set.of(
      "audio/wav", "audio/x-wav", "audio/flac", "audio/x-flac");
  /** 500 MB */
  private static final long MAX_BYTES = 500L * 1024 * 1024;

  /**
   * Default per-track price (pesewas) applied to a newly-attached track. {@link
   * org.shakvilla.beatzmedia.platform.domain.PlatformSettings} carries no default-track-price
   * accessor as of WU-CAT-5 — the artist sets the real price via {@code PATCH .../tracks}
   * (Task 6). Promote to a PlatformSettings-backed constant if/when one is added (never hard-code
   * elsewhere; this is the single call site).
   */
  private static final long DEFAULT_TRACK_PRICE_MINOR = 0L;

  private final CatalogRepository repo;
  private final UploadOriginalUseCase uploadOriginalUseCase;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;

  @Inject
  public UploadReleaseTrackService(
      CatalogRepository repo,
      UploadOriginalUseCase uploadOriginalUseCase,
      IdGenerator ids,
      Clock clock,
      AuditWriter auditWriter) {
    this.repo = repo;
    this.uploadOriginalUseCase = uploadOriginalUseCase;
    this.ids = ids;
    this.clock = clock;
    this.auditWriter = auditWriter;
  }

  @Override
  @Transactional
  public UploadedTrackView upload(ReleaseId releaseId, ArtistId artistId, AudioUpload upload) {
    // Validate format before touching persistence (LLFR-CATALOG-02.4)
    String ct = upload.contentType() != null ? upload.contentType().toLowerCase() : "";
    if (!ALLOWED_TYPES.contains(ct)) {
      throw new UnsupportedFormatException("Only WAV/FLAC accepted, got: " + upload.contentType());
    }
    if (upload.sizeBytes() > MAX_BYTES) {
      throw new FileTooLargeException("Upload exceeds 500 MB limit");
    }

    Release release = repo.findRelease(releaseId)
        .orElseThrow(() -> new ReleaseNotFoundException(releaseId.value()));
    if (!release.getArtistId().equals(artistId.value())) {
      throw new UnauthorizedException("Not your release");
    }
    if (release.getStatus() != ReleaseStatus.draft) {
      throw new IllegalTransitionException(release.getStatus(), "UPLOAD_TRACK");
    }

    String trackId = ids.newId();
    String title = filenameWithoutExtension(upload.filename());

    OwnerRef ownerRef = new OwnerRef("catalog", trackId);
    UploadCommand cmd = new UploadCommand(
        ownerRef,
        MediaKind.AUDIO,
        upload.filename(),
        upload.contentType(),
        upload.sizeBytes(),
        upload.body(),
        upload.contentHash());

    MediaHandle handle = uploadOriginalUseCase.uploadOriginal(cmd);

    // Persist stub track in catalog (status = "uploading")
    Track stubTrack = new Track(
        new TrackId(trackId),
        title,
        artistId,
        null, // artistName resolved later
        null, // albumId
        null, // albumTitle
        handle.durationSec(),
        "/images/placeholder.jpg",
        OwnershipStatus.free,
        null, // priceMinor
        0L,
        null, // audioUrl — set after transcoding
        null, // credits
        null, // quality
        null, // year
        "uploading");

    repo.saveTrack(stubTrack);

    // WU-CAT-5 fix: attach the newly-uploaded track to its draft release (previously orphaned).
    Instant now = clock.now();
    int position = release.getTracks().size();
    release.addTrack(new ReleaseTrack(trackId, position, DEFAULT_TRACK_PRICE_MINOR), now);
    repo.saveRelease(release);

    // INV-10: audit privileged mutation atomically in the same transaction (symmetric with
    // RemoveReleaseTrackService's REMOVE_RELEASE_TRACK entry).
    auditWriter.append(new AuditEntry(
        ids.newId(),
        artistId.value(),
        "UPLOAD_RELEASE_TRACK",
        "Release",
        releaseId.value(),
        AuditType.CATALOG,
        null,
        now));

    return new UploadedTrackView(
        trackId,
        title,
        handle.durationSec(),
        "uploading",
        0,
        null,
        MoneyView.ofMinor(DEFAULT_TRACK_PRICE_MINOR),
        false,
        position);
  }

  private String filenameWithoutExtension(String filename) {
    if (filename == null) return "Untitled";
    int dot = filename.lastIndexOf('.');
    return dot > 0 ? filename.substring(0, dot) : filename;
  }
}
