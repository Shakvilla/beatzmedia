package org.shakvilla.beatzmedia.studio.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.studio.application.port.in.AnalyticsView;
import org.shakvilla.beatzmedia.studio.application.port.in.AudienceView;
import org.shakvilla.beatzmedia.studio.application.port.in.GetAnalytics;
import org.shakvilla.beatzmedia.studio.application.port.in.GetAudience;
import org.shakvilla.beatzmedia.studio.domain.AnalyticsRange;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.AudienceRange;

/**
 * Thin REST resource for the Studio analytics/audience read endpoints (LLFR-STUDIO-03.1 – 03.2).
 * Maps HTTP to input ports; no business logic here — all computation is delegated to {@code
 * analytics} via the {@code AnalyticsReader} output port (see {@code GetAnalyticsService}/{@code
 * GetAudienceService}). Studio ADD §5.1 / §15 (WU-STU-3).
 *
 * <ul>
 *   <li>GET /v1/studio/analytics?range=7d|28d|90d|12m|all → 200 {@link AnalyticsView}; 422 {@code
 *       INVALID_RANGE}.
 *   <li>GET /v1/studio/audience?range=7d|28d|90d|12m → 200 {@link AudienceView}; 422 {@code
 *       INVALID_RANGE}.
 * </ul>
 *
 * <p><strong>IDOR (Layer-2 ownership) — hard security requirement.</strong> The caller's artist id
 * is resolved EXCLUSIVELY from {@code jwt.getSubject()}. There is no {@code artistId} path or query
 * parameter on either endpoint, and the {@code analytics} rollups are queried scoped to that id
 * only — a fan or a different artist's JWT can never retrieve another artist's insights through
 * this resource. This mirrors {@code StudioProfileResource}/{@code StudioPodcastResource}'s exact
 * pattern and directly resolves the carryover note WU-ANA-1 left for this endpoint.
 *
 * <p>Auth: every endpoint requires {@code roles ∋ artist} (else 403), enforced with {@code
 * @RolesAllowed} — same mechanism as every other Studio resource.
 */
@Path("/v1/studio")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("artist")
public class StudioAnalyticsResource {

  private final GetAnalytics getAnalytics;
  private final GetAudience getAudience;
  private final JsonWebToken jwt;

  @Inject
  public StudioAnalyticsResource(GetAnalytics getAnalytics, GetAudience getAudience, JsonWebToken jwt) {
    this.getAnalytics = getAnalytics;
    this.getAudience = getAudience;
    this.jwt = jwt;
  }

  /** GET /v1/studio/analytics — LLFR-STUDIO-03.1. */
  @GET
  @Path("/analytics")
  public AnalyticsView analytics(@QueryParam("range") String range) {
    return getAnalytics.get(artistId(), AnalyticsRange.fromWire(range));
  }

  /** GET /v1/studio/audience — LLFR-STUDIO-03.2. */
  @GET
  @Path("/audience")
  public AudienceView audience(@QueryParam("range") String range) {
    return getAudience.get(artistId(), AudienceRange.fromWire(range));
  }

  private ArtistId artistId() {
    return new ArtistId(jwt.getSubject());
  }
}
