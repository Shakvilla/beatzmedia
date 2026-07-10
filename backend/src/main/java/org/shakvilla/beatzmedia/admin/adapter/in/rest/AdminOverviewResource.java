package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.shakvilla.beatzmedia.admin.application.port.in.GetHealth;
import org.shakvilla.beatzmedia.admin.application.port.in.GetOverview;
import org.shakvilla.beatzmedia.admin.domain.AdminRange;

/**
 * Thin REST resource for the admin overview/health endpoints (LLFR-ADMIN-01.1/.2). Maps DTOs to
 * input-port calls; no business logic here. Admin ADD §5.1.
 *
 * <ul>
 *   <li>GET /v1/admin/overview?range=24h|7d|30d → 200 {@link AdminOverviewDto}; 422 {@code
 *       INVALID_RANGE} on an unrecognised range value.
 *   <li>GET /v1/admin/health → 200 {@link HealthDto}.
 * </ul>
 *
 * <p><strong>RBAC (admin ADD §8).</strong> Overview/health is {@code R} for every admin role —
 * inbound {@code @RolesAllowed} accepts all five, same as {@code AdminSupportResource}; the
 * application layer performs no additional narrowing (reads are never audited, admin ADD §9).
 */
@Path("/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"super-admin", "finance", "moderator", "editor", "support"})
@Tag(name = "admin-overview")
@SecurityRequirement(name = "BearerAuth")
public class AdminOverviewResource {

  private final GetOverview getOverview;
  private final GetHealth getHealth;

  @Inject
  public AdminOverviewResource(GetOverview getOverview, GetHealth getHealth) {
    this.getOverview = getOverview;
    this.getHealth = getHealth;
  }

  /** GET /v1/admin/overview?range=24h|7d|30d — LLFR-ADMIN-01.1. */
  @GET
  @Path("/overview")
  @Operation(summary = "Platform overview KPIs")
  @APIResponse(responseCode = "200", description = "Overview KPIs, GMV series, top artists")
  @APIResponse(responseCode = "422", description = "Unrecognised range value")
  public AdminOverviewDto overview(
      @Parameter(description = "24h|7d|30d") @QueryParam("range") String range) {
    return AdminOverviewDto.from(getOverview.overview(AdminRange.fromWireValue(range)));
  }

  /** GET /v1/admin/health — LLFR-ADMIN-01.2. */
  @GET
  @Path("/health")
  @Operation(summary = "Platform health snapshot")
  @APIResponse(responseCode = "200", description = "Health status, metrics, incidents")
  public HealthDto health() {
    return HealthDto.from(getHealth.health());
  }
}
