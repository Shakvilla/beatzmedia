package org.shakvilla.beatzmedia.studio.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.studio.application.port.in.AudienceView;
import org.shakvilla.beatzmedia.studio.application.port.in.GetAudience;
import org.shakvilla.beatzmedia.studio.application.port.out.AnalyticsReader;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.AudienceRange;

/**
 * Application service for LLFR-STUDIO-03.2 (studio audience read). {@code artist} is always the
 * caller resolved from the JWT subject — the REST resource never accepts an {@code artistId} path
 * or query param (IDOR prevention; a carryover note from WU-ANA-1 explicitly flagged this). All
 * computation is delegated to {@code analytics} via the {@link AnalyticsReader} output port; this
 * service only maps the result to the wire shape. Studio ADD §4.1 / §15.
 */
@ApplicationScoped
public class GetAudienceService implements GetAudience {

  private final AnalyticsReader analyticsReader;

  @Inject
  public GetAudienceService(AnalyticsReader analyticsReader) {
    this.analyticsReader = analyticsReader;
  }

  @Override
  public AudienceView get(ArtistId artist, AudienceRange range) {
    AnalyticsReader.Insights insights = analyticsReader.readInsights(artist, range.toAnalyticsRange());
    return AudienceMapper.toView(insights, range);
  }
}
