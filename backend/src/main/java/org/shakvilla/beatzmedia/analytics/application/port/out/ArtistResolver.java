package org.shakvilla.beatzmedia.analytics.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * Output port: resolves a track id to its owning artist id, implemented by calling catalog's
 * {@code GetTrack} INPUT port in-process (never a catalog table read) — mirrors the exact pattern
 * used by {@code playback.adapter.out.integration.CatalogReaderAdapter}. Consumed by the
 * {@code PlayRecorded} event observer to attribute a play to an artist for the audience rollup.
 * Analytics ADD §4.1.
 */
public interface ArtistResolver {

  Optional<ArtistId> artistOfTrack(TrackId track);
}
