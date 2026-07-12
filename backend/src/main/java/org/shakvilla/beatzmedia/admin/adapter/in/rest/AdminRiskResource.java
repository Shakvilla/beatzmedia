package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.shakvilla.beatzmedia.admin.application.port.in.GetRisk;
import org.shakvilla.beatzmedia.admin.application.port.in.RiskActions;

/**
 * Thin REST resource for the trust &amp; safety endpoints (LLFR-ADMIN-07.1). Maps DTOs to input-port
 * calls; no business logic here. Admin ADD §5.1 / §12.
 *
 * <ul>
 *   <li>GET /v1/admin/risk → 200 {@link RiskBoardDto} (KPIs + risk signals)
 *   <li>POST /v1/admin/risk/:id/review → 200 {@link RiskSignalDto} (404, 409)
 *   <li>POST /v1/admin/risk/:id/clear → 200 {@link RiskSignalDto} (404, 409)
 *   <li>POST /v1/admin/risk/:id/ban { reason } → 200 {@link RiskSignalDto} (422 blank reason, 404, 409)
 * </ul>
 *
 * <p><strong>Actor resolution.</strong> Every mutation's actor is {@code jwt.getSubject()} only —
 * never a body/path parameter (IDOR prevention). <strong>RBAC (admin ADD §12).</strong> All risk
 * endpoints require {@code moderator} or {@code super-admin}. {@code ban}'s {@code reason} is
 * required — {@code @NotBlank} → 422 (same as suspend/takedown; the §12 {@code REASON_REQUIRED}
 * label maps to the uniform 422 {@code VALIDATION} envelope).
 */
@Path("/v1/admin/risk")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"moderator", "super-admin"})
@Tag(name = "admin-risk")
@SecurityRequirement(name = "BearerAuth")
public class AdminRiskResource {

  private final GetRisk getRisk;
  private final RiskActions riskActions;
  private final JsonWebToken jwt;

  @Inject
  public AdminRiskResource(GetRisk getRisk, RiskActions riskActions, JsonWebToken jwt) {
    this.getRisk = getRisk;
    this.riskActions = riskActions;
    this.jwt = jwt;
  }

  /** GET /v1/admin/risk — LLFR-ADMIN-07.1. */
  @GET
  @Operation(summary = "Trust & safety risk board (KPIs + risk signals)")
  @APIResponse(responseCode = "200", description = "Risk KPIs and signals")
  public RiskBoardDto board() {
    return RiskBoardDto.from(getRisk.board());
  }

  /** POST /v1/admin/risk/:id/review — LLFR-ADMIN-07.1. */
  @POST
  @Path("/{id}/review")
  @Operation(summary = "Acknowledge a risk signal as reviewed")
  @APIResponse(responseCode = "200", description = "Signal (unchanged status)")
  @APIResponse(responseCode = "404", description = "Signal not found")
  @APIResponse(responseCode = "409", description = "Illegal transition (signal not open)")
  public RiskSignalDto review(@PathParam("id") String id) {
    return RiskSignalDto.from(riskActions.review(jwt.getSubject(), id));
  }

  /** POST /v1/admin/risk/:id/clear — LLFR-ADMIN-07.1. */
  @POST
  @Path("/{id}/clear")
  @Operation(summary = "Clear a risk signal")
  @APIResponse(responseCode = "200", description = "Signal now cleared")
  @APIResponse(responseCode = "404", description = "Signal not found")
  @APIResponse(responseCode = "409", description = "Illegal transition (signal not open)")
  public RiskSignalDto clear(@PathParam("id") String id) {
    return RiskSignalDto.from(riskActions.clear(jwt.getSubject(), id));
  }

  /** POST /v1/admin/risk/:id/ban { reason } — LLFR-ADMIN-07.1. */
  @POST
  @Path("/{id}/ban")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Ban a risk signal's subject account")
  @APIResponse(responseCode = "200", description = "Signal now banned; subject account banned")
  @APIResponse(responseCode = "422", description = "Blank/missing reason")
  @APIResponse(responseCode = "404", description = "Signal not found (or subject is not an account)")
  @APIResponse(responseCode = "409", description = "Illegal transition (signal not open)")
  public RiskSignalDto ban(@PathParam("id") String id, @Valid BanRequest request) {
    return RiskSignalDto.from(riskActions.ban(jwt.getSubject(), id, request.reason()));
  }

  /** Ban request body: {@code { reason }} — required, 422 if blank/missing. */
  public record BanRequest(@NotBlank String reason) {}
}
