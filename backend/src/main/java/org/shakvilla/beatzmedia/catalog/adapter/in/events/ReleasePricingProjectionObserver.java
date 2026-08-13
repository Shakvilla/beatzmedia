package org.shakvilla.beatzmedia.catalog.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseWentLive;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * Projects each track's price onto its {@code track} row when a release goes live.
 *
 * <p><strong>Why this exists.</strong> The artist authors a price on {@code release_track}, but
 * everything that decides whether a track can be SOLD reads {@code track.ownership} /
 * {@code track.price_minor}: commerce's {@code CatalogPricingServiceAdapter.priceTrack} and
 * catalog's own fan-facing {@code trackToDomain}. Nothing bridged the two.
 *
 * <p>The three writers of those columns were the upload stub ({@code free}, {@code null}), the
 * media-ready projection (duration only) and the cover-art service (image only) — so
 * {@code OwnershipStatus.for_sale} was never assigned anywhere in production. Every uploaded track
 * stayed free and unpurchasable forever: add-to-cart answered {@code 404 Price unavailable}, and
 * fans saw every track as free regardless of what the artist charged, on a buy-to-own platform.
 *
 * <p>It stayed invisible because the commerce tests seed {@code for-sale} directly into the track
 * table, asserting a state production could never produce.
 *
 * <p>Going live is the right moment: it is exactly when a release becomes purchasable. This follows
 * the {@code ReleaseWentLive} / {@code AFTER_SUCCESS} shape the album and search projections
 * already use, so nothing is projected for a transition that rolled back.
 *
 * <p><strong>Not wired to {@code ContentTakenDown}.</strong> A takedown removes discoverability —
 * the search document and the album projection — but existing owners keep their access (PRD OQ-8).
 * Resetting a taken-down track's price to free would misreport what it was sold for.
 */
@ApplicationScoped
public class ReleasePricingProjectionObserver {

  private static final Logger LOG = Logger.getLogger(ReleasePricingProjectionObserver.class);

  private final CatalogRepository repo;

  @Inject
  public ReleasePricingProjectionObserver(CatalogRepository repo) {
    this.repo = repo;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onReleaseWentLive(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) ReleaseWentLive event) {
    Release release = repo.findRelease(new ReleaseId(event.releaseId())).orElse(null);
    if (release == null) {
      // The release outlived the event (deleted mid-publish). Not an error, but silence here is
      // what made the original bug invisible for this long, so say something.
      LOG.warnf(
          "catalog: ReleaseWentLive for unknown release %s — no pricing projected",
          event.releaseId());
      return;
    }

    for (ReleaseTrack rt : release.getTracks()) {
      repo.findTrack(new TrackId(rt.trackId()))
          .ifPresent(
              track -> {
                Track priced = track.withReleasePricing(rt.priceMinor());
                // Idempotent: an unchanged projection returns the same instance, so a re-publish
                // (reinstate re-fires this event) costs no write.
                if (priced != track) {
                  repo.saveTrack(priced);
                  LOG.infof(
                      "catalog: track %s priced at %d minor units from release %s",
                      rt.trackId(), rt.priceMinor(), event.releaseId());
                }
              });
    }
  }
}
