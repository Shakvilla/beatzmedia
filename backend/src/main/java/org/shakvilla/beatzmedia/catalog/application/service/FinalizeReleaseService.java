package org.shakvilla.beatzmedia.catalog.application.service;

import java.time.Instant;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.catalog.application.port.in.FinalizeRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseDetailView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.IdempotencyConflictException;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.TrackCountInvalidException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;

/**
 * Application service for {@link FinalizeRelease}. Validates the INV-12 track-count matrix for
 * the release's {@link ReleaseType}, transitions {@code draft -> in_review} (recomputing the
 * INV-5 list price via {@link Release#submit}), and appends a {@code SUBMIT_RELEASE} audit entry
 * (INV-10) — all in one transaction. Idempotency-Key required: a replay with the same key returns
 * the previously-finalized view without re-transitioning or re-auditing. Catalog ADD §4.1 /
 * WU-CAT-5.
 */
@ApplicationScoped
public class FinalizeReleaseService implements FinalizeRelease {

  private final CatalogRepository repo;
  private final PlatformSettingsProvider settings;
  private final Clock clock;
  private final IdGenerator ids;
  private final AuditWriter auditWriter;

  @Inject
  public FinalizeReleaseService(
      CatalogRepository repo,
      PlatformSettingsProvider settings,
      Clock clock,
      IdGenerator ids,
      AuditWriter auditWriter) {
    this.repo = repo;
    this.settings = settings;
    this.clock = clock;
    this.ids = ids;
    this.auditWriter = auditWriter;
  }

  @Override
  @Transactional
  public StudioReleaseDetailView finalize(ReleaseId id, ArtistId artistId, String idempotencyKey) {
    // Idempotent replay: same key -> same result, no re-transition, no re-audit. Scoped to the
    // SAME release + SAME owning artist as this call — findReleaseByIdempotencyKey is a global
    // lookup, so an unscoped return here would let a reused key (accidentally or maliciously bound
    // to a different release, possibly another artist's) leak that release's detail view (IDOR).
    Optional<Release> seen = repo.findReleaseByIdempotencyKey(idempotencyKey);
    if (seen.isPresent()) {
      Release seenRelease = seen.get();
      if (seenRelease.getId().equals(id.value())
          && seenRelease.getArtistId().equals(artistId.value())) {
        return toDetailView(seenRelease);
      }
      throw new IdempotencyConflictException(
          "Idempotency-Key already used for a different release");
    }

    Release release = repo.findRelease(id)
        .orElseThrow(() -> new ReleaseNotFoundException(id.value()));
    if (!release.getArtistId().equals(artistId.value())) {
      throw new UnauthorizedException("Not your release");
    }

    validateTrackCount(release.getType(), release.getTracks().size());

    Instant now = clock.now();
    // draft -> in_review; recomputes listPriceMinor (INV-5). Throws IllegalTransitionException
    // if not draft.
    release.submit(settings.current().bundleDiscountPct(), now);
    repo.saveReleaseWithIdempotencyKey(release, idempotencyKey);

    // INV-10: audit privileged mutation atomically in the same transaction
    auditWriter.append(new AuditEntry(
        ids.newId(),
        artistId.value(),
        "SUBMIT_RELEASE",
        "Release",
        release.getId(),
        AuditType.CATALOG,
        null,
        now));

    return toDetailView(release);
  }

  /** INV-12: single=1, ep=3-6, album>=7, mixtape>=1. */
  private void validateTrackCount(ReleaseType type, int count) {
    boolean valid = switch (type) {
      case single -> count == 1;
      case ep -> count >= 3 && count <= 6;
      case album -> count >= 7;
      case mixtape -> count >= 1;
    };
    if (!valid) {
      throw new TrackCountInvalidException(
          "Track count " + count + " is invalid for release type '" + type + "'");
    }
  }

  private StudioReleaseDetailView toDetailView(Release r) {
    var tracks = repo.tracksByIds(r.getTracks().stream().map(ReleaseTrack::trackId).toList());
    return ReleaseViewMapper.toDetailView(r, tracks);
  }
}
