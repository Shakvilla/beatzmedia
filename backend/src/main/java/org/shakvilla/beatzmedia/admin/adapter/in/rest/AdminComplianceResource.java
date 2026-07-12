package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.shakvilla.beatzmedia.admin.application.port.in.ComplianceActions;
import org.shakvilla.beatzmedia.admin.application.port.in.ListCompliance;
import org.shakvilla.beatzmedia.admin.domain.ComplianceType;

/**
 * Thin REST resource for the compliance endpoints (LLFR-ADMIN-09.1). Maps DTOs to input-port calls;
 * no business logic here. Admin ADD §5.1 / §12.
 *
 * <ul>
 *   <li>GET /v1/admin/compliance?type= → 200 {@link ComplianceRequestDto}[] (bare array; 422 on bad type)
 *   <li>POST /v1/admin/compliance/:id/start → 200 {@link ComplianceRequestDto} (404, 409)
 *   <li>POST /v1/admin/compliance/:id/complete → 200 {@link ComplianceRequestDto} (404, 409)
 *   <li>POST /v1/admin/compliance/:id/export → 202 {@link DataExportJobRefDto} (404)
 *   <li>POST /v1/admin/compliance/:id/notice → 200 {@link ComplianceRequestDto} (404)
 * </ul>
 *
 * <p><strong>Actor resolution.</strong> Every mutation's actor is {@code jwt.getSubject()} only.
 * <strong>RBAC (admin ADD §12, OQ-1).</strong> All compliance endpoints require {@code super-admin}.
 */
@Path("/v1/admin/compliance")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("super-admin")
@Tag(name = "admin-compliance")
@SecurityRequirement(name = "BearerAuth")
public class AdminComplianceResource {

  private final ListCompliance listCompliance;
  private final ComplianceActions complianceActions;
  private final JsonWebToken jwt;

  @Inject
  public AdminComplianceResource(
      ListCompliance listCompliance, ComplianceActions complianceActions, JsonWebToken jwt) {
    this.listCompliance = listCompliance;
    this.complianceActions = complianceActions;
    this.jwt = jwt;
  }

  /** GET /v1/admin/compliance?type=DSAR-export|DSAR-delete|Takedown|Tax — LLFR-ADMIN-09.1. */
  @GET
  @Operation(summary = "List compliance requests (DSAR/takedown/tax)")
  @APIResponse(responseCode = "200", description = "Compliance requests (bare array)")
  @APIResponse(responseCode = "422", description = "Unrecognised type filter value")
  public List<ComplianceRequestDto> list(
      @Parameter(description = "DSAR-export|DSAR-delete|Takedown|Tax") @QueryParam("type")
          String typeParam) {
    ComplianceType type = ComplianceType.fromWireValue(typeParam);
    return listCompliance.list(type).stream().map(ComplianceRequestDto::from).toList();
  }

  /** POST /v1/admin/compliance/:id/start — LLFR-ADMIN-09.1. */
  @POST
  @Path("/{id}/start")
  @Operation(summary = "Start work on a compliance request")
  @APIResponse(responseCode = "200", description = "Request now in_progress")
  @APIResponse(responseCode = "404", description = "Request not found")
  @APIResponse(responseCode = "409", description = "Illegal transition")
  public ComplianceRequestDto start(@PathParam("id") String id) {
    return ComplianceRequestDto.from(complianceActions.start(jwt.getSubject(), id));
  }

  /** POST /v1/admin/compliance/:id/complete — LLFR-ADMIN-09.1. */
  @POST
  @Path("/{id}/complete")
  @Operation(summary = "Complete a compliance request")
  @APIResponse(responseCode = "200", description = "Request now completed")
  @APIResponse(responseCode = "404", description = "Request not found")
  @APIResponse(responseCode = "409", description = "Illegal transition")
  public ComplianceRequestDto complete(@PathParam("id") String id) {
    return ComplianceRequestDto.from(complianceActions.complete(jwt.getSubject(), id));
  }

  /** POST /v1/admin/compliance/:id/export — LLFR-ADMIN-09.1 (DSAR data export; 202). */
  @POST
  @Path("/{id}/export")
  @Operation(summary = "Enqueue a DSAR data export for the request subject")
  @APIResponse(responseCode = "202", description = "Export job queued")
  @APIResponse(responseCode = "404", description = "Request not found")
  public Response export(@PathParam("id") String id) {
    DataExportJobRefDto body =
        DataExportJobRefDto.from(complianceActions.export(jwt.getSubject(), id));
    return Response.status(Response.Status.ACCEPTED).entity(body).build();
  }

  /** POST /v1/admin/compliance/:id/notice — LLFR-ADMIN-09.1 (DMCA notice). */
  @POST
  @Path("/{id}/notice")
  @Operation(summary = "Record a compliance/DMCA notice against the request")
  @APIResponse(responseCode = "200", description = "Notice recorded")
  @APIResponse(responseCode = "404", description = "Request not found")
  public ComplianceRequestDto notice(@PathParam("id") String id) {
    return ComplianceRequestDto.from(complianceActions.notice(jwt.getSubject(), id));
  }
}
