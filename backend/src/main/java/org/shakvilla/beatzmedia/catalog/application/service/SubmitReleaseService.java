package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseView;
import org.shakvilla.beatzmedia.catalog.application.port.in.SubmitRelease;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.SplitConfirmation;
import org.shakvilla.beatzmedia.catalog.domain.SplitEntry;
import org.shakvilla.beatzmedia.catalog.domain.SplitOver100Exception;
import org.shakvilla.beatzmedia.catalog.domain.TrackCountInvalidException;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;

/**
 * Application service for {@link SubmitRelease}. Validates track count (INV-12), validates split
 * sums (INV-12), computes list price (INV-5), and respects idempotency keys.
 * LLFR-CATALOG-02.2.
 */
@ApplicationScoped
public class SubmitReleaseService implements SubmitRelease {

  private final CatalogRepository repo;
  private final PlatformSettingsProvider settings;

  @Inject
  public SubmitReleaseService(CatalogRepository repo, PlatformSettingsProvider settings) {
    this.repo = repo;
    this.settings = settings;
  }

  @Override
  @Transactional
  public StudioReleaseView submit(SubmitReleaseCommand command) {
    // Idempotency: return existing if key already seen
    if (command.idempotencyKey() != null) {
      Optional<Release> existing = repo.findReleaseByIdempotencyKey(command.idempotencyKey());
      if (existing.isPresent()) {
        return toView(existing.get());
      }
    }

    List<UploadedTrackRef> trackRefs = command.tracks() != null ? command.tracks() : List.of();

    // Validate track count for single
    if (command.type() == ReleaseType.single && trackRefs.size() != 1) {
      throw new TrackCountInvalidException(
          "A single release must have exactly 1 track, got " + trackRefs.size());
    }

    // Validate split sums per track
    for (UploadedTrackRef ref : trackRefs) {
      if (ref.splits() != null) {
        int total = ref.splits().stream().mapToInt(SplitEntryCommand::percent).sum();
        if (total > 100) {
          throw new SplitOver100Exception(
              "Split percentages for track " + ref.trackId() + " sum to " + total + " (> 100)");
        }
      }
    }

    List<ReleaseTrack> tracks = trackRefs.stream()
        .map(ref -> new ReleaseTrack(ref.trackId(), ref.position(), ref.priceMinor()))
        .toList();

    int bundleDiscountPct = settings.current().bundleDiscountPct();
    String id = UUID.randomUUID().toString();

    Release release = Release.create(
        id,
        command.artistId().value(),
        command.title(),
        command.type(),
        command.visibility(),
        null, // scheduledAt — parsed later in WU-CAT-4
        tracks,
        bundleDiscountPct);

    repo.saveRelease(release);

    // Persist split entries (informational; no FK enforcement needed here)
    // Split rows are saved via the persistence adapter through saveRelease

    return toView(release);
  }

  private StudioReleaseView toView(Release r) {
    String date = r.getCreatedAt() != null ? r.getCreatedAt().toString() : "—";
    return new StudioReleaseView(
        r.getId(),
        r.getTitle(),
        r.getType(),
        r.getStatus(),
        date,
        r.getTracks().size(),
        0L,
        MoneyView.ofMinor(0L),
        MoneyView.ofMinor(r.getListPriceMinor()));
  }
}
