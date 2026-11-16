package org.shakvilla.beatzmedia.catalog.application.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.catalog.application.port.in.ManageReleaseRevision;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.TrackSearchProjection;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseRevision;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseRevisionApproved;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseRevisionStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseRevisionTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.media.application.port.in.AllowedAudioTypes;
import org.shakvilla.beatzmedia.media.application.port.in.PromoteRevisionAudio;
import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.port.in.UploadOriginalUseCase;
import org.shakvilla.beatzmedia.media.application.port.out.MediaService;
import org.shakvilla.beatzmedia.media.domain.FileTooLargeException;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.media.domain.UnsupportedFormatException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

@ApplicationScoped
public class ManageReleaseRevisionService implements ManageReleaseRevision {
  private static final long MAX_AUDIO_BYTES = 500L * 1024 * 1024;
  private static final String IMAGE_URL_PREFIX = "/v1/media/images/";

  private final CatalogRepository catalog;
  private final UploadOriginalUseCase upload;
  private final MediaService media;
  private final PromoteRevisionAudio promoteAudio;
  private final TrackSearchProjection search;
  private final Event<ReleaseRevisionApproved> approvedEvent;
  private final AuditWriter audit;
  private final Clock clock;
  private final IdGenerator ids;
  private final PlatformSettingsProvider settings;

  @Inject
  public ManageReleaseRevisionService(
      CatalogRepository catalog,
      UploadOriginalUseCase upload,
      MediaService media,
      PromoteRevisionAudio promoteAudio,
      TrackSearchProjection search,
      Event<ReleaseRevisionApproved> approvedEvent,
      AuditWriter audit,
      Clock clock,
      IdGenerator ids,
      PlatformSettingsProvider settings) {
    this.catalog = catalog;
    this.upload = upload;
    this.media = media;
    this.promoteAudio = promoteAudio;
    this.search = search;
    this.approvedEvent = approvedEvent;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
    this.settings = settings;
  }

  @Override
  @Transactional
  public ReleaseRevision get(ReleaseId releaseId, ArtistId artistId) {
    Release release = ownedRelease(releaseId, artistId);
    if (release.getStatus() == ReleaseStatus.in_review) {
      return snapshot(release, release.getId(), ReleaseRevisionStatus.in_review);
    }
    return catalog.findLatestRevision(releaseId)
        .orElseGet(() -> snapshot(release, ids.newId(), ReleaseRevisionStatus.approved));
  }

  @Override
  @Transactional
  public ReleaseRevision getForModeration(ReleaseId releaseId) {
    Release release = catalog.findRelease(releaseId)
        .orElseThrow(() -> new ReleaseNotFoundException(releaseId.value()));
    return catalog.findPendingRevision(releaseId)
        .filter(r -> r.status() == ReleaseRevisionStatus.in_review)
        .orElseThrow(() -> new NotFoundException("Pending release revision not found"));
  }

  @Override
  @Transactional
  public ReleaseRevision save(ReleaseId releaseId, ArtistId artistId, EditCommand command) {
    Release release = ownedRelease(releaseId, artistId);
    if (release.getStatus() == ReleaseStatus.in_review) {
      return savePendingSubmission(release, artistId, command);
    }
    requireRevisionEligible(release);
    ReleaseRevision revision = editableRevision(release);
    validateFixedMembership(release, command.tracks());
    List<ReleaseRevisionTrack> tracks = command.tracks().stream().map(edit -> {
      ReleaseRevisionTrack old = revision.tracks().stream()
          .filter(t -> t.trackId().equals(edit.trackId())).findFirst().orElseThrow();
      boolean custom = old.customArtwork() || !Objects.equals(old.image(), edit.image());
      return new ReleaseRevisionTrack(edit.trackId(), edit.position(), edit.priceMinor(),
          edit.title(), edit.image(), custom, old.audioAssetId(), old.durationSec());
    }).toList();
    ReleaseRevision updated = revision.updated(
        command.title(), command.genre(), command.description(),
        Visibility.fromDbValue(command.visibility()), command.scheduledAt(), command.coverImage(),
        command.downloadable(), tracks, clock.now());
    catalog.saveRevision(updated);
    audit("SAVE_RELEASE_REVISION", artistId.value(), releaseId.value());
    return updated;
  }

  @Override
  @Transactional
  public ReleaseRevision submit(ReleaseId releaseId, ArtistId artistId) {
    Release release = ownedRelease(releaseId, artistId);
    if (release.getStatus() == ReleaseStatus.in_review) {
      return snapshot(release, release.getId(), ReleaseRevisionStatus.in_review);
    }
    requireRevisionEligible(release);
    ReleaseRevision revision = catalog.findPendingRevision(releaseId)
        .orElseThrow(() -> new NotFoundException("Save release changes before submitting"));
    if (revision.status() != ReleaseRevisionStatus.draft) {
      throw new IllegalTransitionException(release.getStatus(), "SUBMIT_RELEASE_REVISION");
    }
    if (release.getType() != ReleaseType.single
        && revision.tracks().stream().anyMatch(t -> !t.customArtwork())) {
      throw new org.shakvilla.beatzmedia.catalog.domain.TrackArtworkRequiredException();
    }
    for (ReleaseRevisionTrack track : revision.tracks()) {
      if (track.audioAssetId() != null && !promoteAudio.isReady(new MediaAssetId(track.audioAssetId()))) {
      throw new ValidationException("Wait for replacement audio to finish processing", "audio");
      }
    }
    ReleaseRevision submitted = revision.submitted(clock.now());
    catalog.saveRevision(submitted);
    audit("SUBMIT_RELEASE_REVISION", artistId.value(), releaseId.value());
    return submitted;
  }

  @Override
  @Transactional
  public String uploadArtwork(ReleaseId releaseId, ArtistId artistId, TrackId trackId, ImageUpload image) {
    Release release = ownedRelease(releaseId, artistId);
    if (release.getStatus() == ReleaseStatus.in_review) {
      Track track = releaseTrack(release, trackId);
      MediaHandle handle = upload.uploadOriginal(new UploadCommand(
          new OwnerRef("catalog", trackId.value()), MediaKind.ARTWORK, image.filename(),
          image.contentType(), image.sizeBytes(), image.body(), image.contentHash()));
      media.processArtwork(handle.assetId());
      String url = IMAGE_URL_PREFIX + handle.assetId().value();
      Track updated = track.withImage(url);
      catalog.saveTrack(updated);
      search.index(updated, false);
      return url;
    }
    requireRevisionEligible(release);
    ReleaseRevision revision = editableRevision(release);
    ReleaseRevisionTrack track = revisionTrack(revision, trackId);
    MediaHandle handle = upload.uploadOriginal(new UploadCommand(
        new OwnerRef("catalog_revision", "track-artwork:" + trackId.value()),
        MediaKind.ARTWORK, image.filename(), image.contentType(), image.sizeBytes(), image.body(), image.contentHash()));
    media.processArtwork(handle.assetId());
    String url = IMAGE_URL_PREFIX + handle.assetId().value();
    catalog.saveRevision(revision.withTrack(new ReleaseRevisionTrack(
        track.trackId(), track.position(), track.priceMinor(), track.title(), url, true,
        track.audioAssetId(), track.durationSec()), clock.now()));
    return url;
  }

  @Override
  @Transactional
  public String uploadReleaseArtwork(ReleaseId releaseId, ArtistId artistId, ImageUpload image) {
    Release release = ownedRelease(releaseId, artistId);
    if (release.getStatus() == ReleaseStatus.in_review) {
      MediaHandle handle = upload.uploadOriginal(new UploadCommand(
          new OwnerRef("catalog", releaseId.value()), MediaKind.ARTWORK, image.filename(),
          image.contentType(), image.sizeBytes(), image.body(), image.contentHash()));
      media.processArtwork(handle.assetId());
      String url = IMAGE_URL_PREFIX + handle.assetId().value();
      release.setCoverImage(url);
      catalog.saveRelease(release);
      for (ReleaseTrack rt : release.getTracks()) {
        catalog.findTrack(new TrackId(rt.trackId())).ifPresent(track -> catalog.saveTrack(track.withReleaseCover(url)));
      }
      return url;
    }
    requireRevisionEligible(release);
    ReleaseRevision revision = editableRevision(release);
    MediaHandle handle = upload.uploadOriginal(new UploadCommand(
        new OwnerRef("catalog_revision", revision.id() + ":cover"), MediaKind.ARTWORK,
        image.filename(), image.contentType(), image.sizeBytes(), image.body(), image.contentHash()));
    media.processArtwork(handle.assetId());
    ReleaseRevision updated = revision.updated(revision.title(), revision.genre(), revision.description(),
        revision.visibility(), revision.scheduledAt(), IMAGE_URL_PREFIX + handle.assetId().value(),
        revision.downloadable(), revision.tracks(), clock.now());
    catalog.saveRevision(updated);
    return updated.coverImage();
  }

  @Override
  @Transactional
  public void uploadAudio(ReleaseId releaseId, ArtistId artistId, TrackId trackId, AudioUpload audio) {
    if (!AllowedAudioTypes.isAllowed(audio.contentType(), audio.filename())) {
      throw new UnsupportedFormatException("Only " + AllowedAudioTypes.DISPLAY + " accepted");
    }
    if (audio.sizeBytes() > MAX_AUDIO_BYTES) throw new FileTooLargeException("Upload exceeds 500 MB limit");
    Release release = ownedRelease(releaseId, artistId);
    if (release.getStatus() == ReleaseStatus.in_review) {
      Track track = releaseTrack(release, trackId);
      MediaHandle handle = upload.uploadOriginal(new UploadCommand(
          new OwnerRef("catalog", trackId.value()), MediaKind.AUDIO, audio.filename(), audio.contentType(),
          audio.sizeBytes(), audio.body(), audio.contentHash()));
      catalog.saveTrack(track.withPendingAudio());
      return;
    }
    requireRevisionEligible(release);
    ReleaseRevision revision = editableRevision(release);
    ReleaseRevisionTrack track = revisionTrack(revision, trackId);
    MediaHandle handle = upload.uploadOriginal(new UploadCommand(
        new OwnerRef("catalog_revision", "track-audio:" + trackId.value()),
        MediaKind.AUDIO, audio.filename(), audio.contentType(), audio.sizeBytes(), audio.body(), audio.contentHash()));
    catalog.saveRevision(revision.withTrack(new ReleaseRevisionTrack(
        track.trackId(), track.position(), track.priceMinor(), track.title(), track.image(),
        track.customArtwork(), handle.assetId().value(), handle.durationSec()), clock.now()));
  }

  @Override
  @Transactional
  public ReleaseRevision approve(String revisionId, String moderatorId) {
    ReleaseRevision revision = catalog.findRevision(revisionId)
        .orElseThrow(() -> new NotFoundException("Release revision not found"));
    if (revision.status() != ReleaseRevisionStatus.in_review) {
      throw new IllegalTransitionException(
          catalog.findRelease(new ReleaseId(revision.releaseId())).orElseThrow().getStatus(),
          "APPROVE_RELEASE_REVISION");
    }
    Release release = catalog.findRelease(new ReleaseId(revision.releaseId()))
        .orElseThrow(() -> new ReleaseNotFoundException(revision.releaseId()));
    validateFixedMembership(release, revision.tracks().stream().map(t -> new TrackEdit(
        t.trackId(), t.position(), t.priceMinor(), t.title(), t.image())).toList());
    for (ReleaseRevisionTrack proposed : revision.tracks()) {
      Track track = catalog.findTrack(new TrackId(proposed.trackId()))
          .orElseThrow(() -> new NotFoundException("Revision track not found"));
      Track updated = track.withTitle(proposed.title()).withImage(proposed.image())
          .withReleasePricing(proposed.priceMinor());
      if (proposed.audioAssetId() != null) {
        MediaHandle promoted = promoteAudio.promote(
            new MediaAssetId(proposed.audioAssetId()), new OwnerRef("catalog", proposed.trackId()));
        updated = updated.withApprovedAudio(promoted.durationSec());
      }
      catalog.saveTrack(updated);
    }
    List<ReleaseTrack> releaseTracks = revision.tracks().stream()
        .map(t -> new ReleaseTrack(t.trackId(), t.position(), t.priceMinor())).toList();
    release.applyApprovedRevision(revision.title(), revision.genre(), revision.description(),
        revision.visibility(), revision.scheduledAt(), revision.coverImage(), revision.downloadable(),
        releaseTracks, settings.current().bundleDiscountPct(), clock.now());
    catalog.saveRelease(release);
    ReleaseRevision approved = revision.approved(clock.now());
    catalog.saveRevision(approved);
    audit("APPROVE_RELEASE_REVISION", moderatorId, revision.releaseId());
    approvedEvent.fire(new ReleaseRevisionApproved(revision.releaseId(), revision.artistId()));
    return approved;
  }

  @Override
  @Transactional
  public ReleaseRevision reject(String revisionId, String moderatorId, String reason) {
    if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Rejection reason is required");
    ReleaseRevision revision = catalog.findRevision(revisionId)
        .orElseThrow(() -> new NotFoundException("Release revision not found"));
    if (revision.status() != ReleaseRevisionStatus.in_review) {
      throw new IllegalTransitionException(
          catalog.findRelease(new ReleaseId(revision.releaseId())).orElseThrow().getStatus(),
          "REJECT_RELEASE_REVISION");
    }
    ReleaseRevision rejected = revision.rejected(reason.trim(), clock.now());
    catalog.saveRevision(rejected);
    audit("REJECT_RELEASE_REVISION", moderatorId, revision.releaseId());
    return rejected;
  }

  private Release ownedRelease(ReleaseId id, ArtistId artistId) {
    Release release = catalog.findRelease(id).orElseThrow(() -> new ReleaseNotFoundException(id.value()));
    if (!release.getArtistId().equals(artistId.value())) throw new NotFoundException("Release not found");
    return release;
  }

  private void requireRevisionEligible(Release release) {
    if (release.getStatus() != ReleaseStatus.live && release.getStatus() != ReleaseStatus.scheduled) {
      throw new IllegalTransitionException(release.getStatus(), "EDIT_RELEASE_REVISION");
    }
  }

  private ReleaseRevision editableRevision(Release release) {
    return catalog.findPendingRevision(new ReleaseId(release.getId()))
        .filter(r -> r.status() == ReleaseRevisionStatus.draft)
        .orElseGet(() -> {
          ReleaseRevision created = snapshot(release, ids.newId(), ReleaseRevisionStatus.draft);
          catalog.saveRevision(created);
          audit("CREATE_RELEASE_REVISION", release.getArtistId(), release.getId());
          return created;
        });
  }

  private ReleaseRevision snapshot(Release release, String revisionId, ReleaseRevisionStatus status) {
    List<ReleaseRevisionTrack> tracks = catalog.tracksByIds(
        release.getTracks().stream().map(ReleaseTrack::trackId).toList()).stream()
        .map(track -> {
          ReleaseTrack relTrack = release.getTracks().stream()
              .filter(rt -> rt.trackId().equals(track.getId().value())).findFirst().orElseThrow();
          return new ReleaseRevisionTrack(track.getId().value(), relTrack.position(), relTrack.priceMinor(),
              track.getTitle(), track.getImage(), track.hasCustomArtwork(), null, track.getDurationSec());
        }).toList();
    Instant now = clock.now();
    return new ReleaseRevision(revisionId, release.getId(), release.getArtistId(), status, 1,
        release.getTitle(), release.getGenre(), release.getDescription(), release.getVisibility(),
        release.getScheduledAt(), release.getCoverImage(), release.getDownloadable(), tracks, null,
        now, now, null, null);
  }

  private ReleaseRevision savePendingSubmission(Release release, ArtistId artistId, EditCommand command) {
    validateFixedMembership(release, command.tracks());
    List<ReleaseTrack> nextReleaseTracks = command.tracks().stream()
        .map(t -> new ReleaseTrack(t.trackId(), t.position(), t.priceMinor())).toList();
    for (TrackEdit edit : command.tracks()) {
      Track track = releaseTrack(release, new TrackId(edit.trackId()));
      Track updated = track.withTitle(edit.title()).withReleasePricing(edit.priceMinor());
      if (!Objects.equals(track.getImage(), edit.image())) updated = updated.withImage(edit.image());
      catalog.saveTrack(updated);
    }
    release.updatePendingSubmission(command.title(), command.genre(), command.description(),
        Visibility.fromDbValue(command.visibility()), command.scheduledAt(), command.coverImage(),
        command.downloadable() == null ? release.getDownloadable() : command.downloadable(),
        nextReleaseTracks, settings.current().bundleDiscountPct(), clock.now());
    catalog.saveRelease(release);
    audit("UPDATE_PENDING_RELEASE", artistId.value(), release.getId());
    return snapshot(release, release.getId(), ReleaseRevisionStatus.in_review);
  }

  private Track releaseTrack(Release release, TrackId trackId) {
    if (release.getTracks().stream().noneMatch(t -> t.trackId().equals(trackId.value()))) {
      throw new NotFoundException("Release track not found");
    }
    return catalog.findTrack(trackId).orElseThrow(() -> new NotFoundException("Release track not found"));
  }

  private void validateFixedMembership(Release release, List<TrackEdit> edits) {
    if (edits == null || edits.isEmpty()) throw new ValidationException("Track edits are required", "tracks");
    Set<String> current = new HashSet<>(release.getTracks().stream().map(ReleaseTrack::trackId).toList());
    Set<String> proposed = new HashSet<>(edits.stream().map(TrackEdit::trackId).toList());
    if (!current.equals(proposed) || current.size() != edits.size()) {
      throw new ValidationException("A submitted release cannot add or remove tracks", "tracks");
    }
    if (edits.stream().map(TrackEdit::position).distinct().count() != edits.size()) {
      throw new ValidationException("Track positions must be unique", "tracks");
    }
  }

  private ReleaseRevisionTrack revisionTrack(ReleaseRevision revision, TrackId trackId) {
    return revision.tracks().stream().filter(t -> t.trackId().equals(trackId.value())).findFirst()
        .orElseThrow(() -> new NotFoundException("Release track not found"));
  }

  private void audit(String action, String actor, String releaseId) {
    Instant now = clock.now();
    audit.append(new AuditEntry(ids.newId(), actor, action, "Release", releaseId,
        AuditType.CATALOG, null, now));
  }
}
