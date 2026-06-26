package org.shakvilla.beatzmedia.catalog.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.application.port.in.GetLyrics;
import org.shakvilla.beatzmedia.catalog.application.port.in.LyricLineView;
import org.shakvilla.beatzmedia.catalog.application.port.in.LyricsView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.LyricsNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * Application service for LLFR-CATALOG-01.6 (lyrics). Catalog ADD §4.1.
 */
@ApplicationScoped
public class GetLyricsService implements GetLyrics {

  private final CatalogRepository catalogRepository;

  @Inject
  public GetLyricsService(CatalogRepository catalogRepository) {
    this.catalogRepository = catalogRepository;
  }

  @Override
  public LyricsView get(TrackId id) {
    return catalogRepository.findLyrics(id)
        .map(lyrics -> new LyricsView(
            lyrics.getLines().stream()
                .map(l -> new LyricLineView(l.tSec(), l.text()))
                .toList()))
        .orElseThrow(() -> new LyricsNotFoundException(id.value()));
  }
}
