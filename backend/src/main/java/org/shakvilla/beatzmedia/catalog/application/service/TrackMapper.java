package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackCreditView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Track;

/**
 * Stateless helper: maps a domain {@link Track} to a {@link TrackView}, applying per-caller
 * ownership/price decoration via {@link OwnershipReader}. Catalog ADD §5.2.
 */
final class TrackMapper {

  private TrackMapper() {}

  static TrackView toView(Track track, Optional<String> callerId, OwnershipReader ownershipReader) {
    OwnershipStatus effectiveOwnership =
        ownershipReader.ownership(track.getId(), callerId);
    Optional<Long> effectivePriceMinor =
        ownershipReader.priceMinor(track.getId(), callerId);

    MoneyView priceView = effectivePriceMinor
        .map(MoneyView::ofMinor)
        .orElse(null);

    List<TrackCreditView> creditViews = track.getCredits()
        .map(cs -> cs.stream()
            .map(c -> new TrackCreditView(c.role(), c.names()))
            .toList())
        .orElse(null);

    return new TrackView(
        track.getId().value(),
        track.getTitle(),
        track.getArtistId().value(),
        track.getArtistName(),
        track.getAlbumId().map(a -> a.value()).orElse(null),
        track.getAlbumTitle().orElse(null),
        track.getDurationSec(),
        track.getImage(),
        effectiveOwnership.wireValue(),
        priceView,
        track.getPlays().orElse(null),
        track.getAudioUrl().orElse(null),
        creditViews,
        track.getQuality().orElse(null),
        track.getYear().orElse(null));
  }
}
