package org.shakvilla.beatzmedia.studio.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.studio.application.port.in.AnalyticsView;
import org.shakvilla.beatzmedia.studio.application.port.in.GetAnalytics;
import org.shakvilla.beatzmedia.studio.application.port.out.AnalyticsReader;
import org.shakvilla.beatzmedia.studio.domain.AnalyticsRange;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * Application service for LLFR-STUDIO-03.1 (studio analytics read). {@code artist} is always the
 * caller resolved from the JWT subject — the REST resource never accepts an {@code artistId} path
 * or query param (IDOR prevention; a carryover note from WU-ANA-1 explicitly flagged this). All
 * computation is delegated to {@code analytics} via the {@link AnalyticsReader} output port; this
 * service only maps the result to the wire shape. Studio ADD §4.1 / §15.
 */
@ApplicationScoped
public class GetAnalyticsService implements GetAnalytics {

  private final AnalyticsReader analyticsReader;

  @Inject
  public GetAnalyticsService(AnalyticsReader analyticsReader) {
    this.analyticsReader = analyticsReader;
  }

  @Override
  public AnalyticsView get(ArtistId artist, AnalyticsRange range) {
    AnalyticsReader.Insights insights = analyticsReader.readInsights(artist, range);
    return AnalyticsMapper.toView(insights);
  }
}
