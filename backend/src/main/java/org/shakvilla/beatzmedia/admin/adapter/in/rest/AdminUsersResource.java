package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
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
import org.shakvilla.beatzmedia.admin.application.port.in.ExportUserData;
import org.shakvilla.beatzmedia.admin.application.port.in.GetUser;
import org.shakvilla.beatzmedia.admin.application.port.in.ImpersonateUser;
import org.shakvilla.beatzmedia.admin.application.port.in.ListUsers;
import org.shakvilla.beatzmedia.admin.application.port.in.ReactivateUser;
import org.shakvilla.beatzmedia.admin.application.port.in.SuspendUser;
import org.shakvilla.beatzmedia.admin.application.port.in.UserQuery;
import org.shakvilla.beatzmedia.admin.application.port.in.VerifyUser;
import org.shakvilla.beatzmedia.admin.domain.UserFilter;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Thin REST resource for the admin user-administration endpoints (LLFR-ADMIN-02.1–.6). Maps DTOs
 * to input-port calls; no business logic here. Admin ADD §5.1.
 *
 * <ul>
 *   <li>GET /v1/admin/users?q=&filter=&page=&size= → 200 {@link PagedUsersDto} (any admin role)
 *   <li>GET /v1/admin/users/:id → 200 {@link UserDetailDto}; 404 if not found
 *   <li>POST /v1/admin/users/:id/verify → 200 {@link AdminUserRowDto} (super-admin, moderator)
 *   <li>POST /v1/admin/users/:id/suspend → 200 {@link AdminUserRowDto} (super-admin, moderator)
 *   <li>POST /v1/admin/users/:id/reactivate → 200 {@link AdminUserRowDto} (super-admin, moderator)
 *   <li>POST /v1/admin/users/:id/impersonate → 200 {@link ImpersonationTokenDto} (super-admin ONLY)
 *   <li>POST /v1/admin/users/:id/data-export → 202 {@link DataExportJobRefDto} (super-admin, support)
 * </ul>
 *
 * <p><strong>Actor resolution.</strong> Every mutation's actor is {@code jwt.getSubject()} only —
 * never a body/path parameter (IDOR prevention, established convention). <strong>RBAC (admin ADD
 * §8).</strong> Read endpoints accept all five admin roles; verify/suspend/reactivate accept
 * super-admin + moderator; impersonate is super-admin ONLY; data-export accepts super-admin +
 * support.
 */
@Path("/v1/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "admin-users")
@SecurityRequirement(name = "BearerAuth")
public class AdminUsersResource {

  private final ListUsers listUsers;
  private final GetUser getUser;
  private final VerifyUser verifyUser;
  private final SuspendUser suspendUser;
  private final ReactivateUser reactivateUser;
  private final ImpersonateUser impersonateUser;
  private final ExportUserData exportUserData;
  private final JsonWebToken jwt;

  @Inject
  public AdminUsersResource(
      ListUsers listUsers,
      GetUser getUser,
      VerifyUser verifyUser,
      SuspendUser suspendUser,
      ReactivateUser reactivateUser,
      ImpersonateUser impersonateUser,
      ExportUserData exportUserData,
      JsonWebToken jwt) {
    this.listUsers = listUsers;
    this.getUser = getUser;
    this.verifyUser = verifyUser;
    this.suspendUser = suspendUser;
    this.reactivateUser = reactivateUser;
    this.impersonateUser = impersonateUser;
    this.exportUserData = exportUserData;
    this.jwt = jwt;
  }

  /** GET /v1/admin/users?q=&filter=fans|artists|verified|suspended&page=&size= — LLFR-ADMIN-02.1. */
  @GET
  @RolesAllowed({"super-admin", "finance", "moderator", "editor", "support"})
  @Operation(summary = "List/search admin users")
  @APIResponse(responseCode = "200", description = "Paged user list with counts")
  @APIResponse(responseCode = "422", description = "Unrecognised filter value")
  public PagedUsersDto list(
      @Parameter(description = "Free-text match on name/email") @QueryParam("q") String q,
      @Parameter(description = "fans|artists|verified|suspended") @QueryParam("filter")
          String filterParam,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("20") int size) {
    UserFilter filter = UserFilter.fromWireValue(filterParam);
    return PagedUsersDto.from(listUsers.list(new UserQuery(q, filter), new PageRequest(page, size)));
  }

  /** GET /v1/admin/users/:id — LLFR-ADMIN-02.1. */
  @GET
  @Path("/{id}")
  @RolesAllowed({"super-admin", "finance", "moderator", "editor", "support"})
  @Operation(summary = "Get a user's admin detail page")
  @APIResponse(responseCode = "200", description = "User detail")
  @APIResponse(responseCode = "404", description = "User not found")
  public UserDetailDto get(@PathParam("id") String id) {
    return UserDetailDto.from(getUser.get(id));
  }

  /** POST /v1/admin/users/:id/verify — LLFR-ADMIN-02.2. */
  @POST
  @Path("/{id}/verify")
  @RolesAllowed({"super-admin", "moderator"})
  @Operation(summary = "Verify an artist account")
  @APIResponse(responseCode = "200", description = "User verified")
  @APIResponse(responseCode = "404", description = "User not found")
  @APIResponse(responseCode = "409", description = "Already verified")
  public AdminUserRowDto verify(@PathParam("id") String id) {
    return AdminUserRowDto.from(verifyUser.verify(jwt.getSubject(), id));
  }

  /** POST /v1/admin/users/:id/suspend { reason } — LLFR-ADMIN-02.3. */
  @POST
  @Path("/{id}/suspend")
  @RolesAllowed({"super-admin", "moderator"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Suspend a user")
  @APIResponse(responseCode = "200", description = "User suspended")
  @APIResponse(responseCode = "422", description = "Blank/missing reason")
  @APIResponse(responseCode = "404", description = "User not found")
  @APIResponse(responseCode = "409", description = "Already suspended")
  public AdminUserRowDto suspend(@PathParam("id") String id, @Valid SuspendRequest request) {
    return AdminUserRowDto.from(suspendUser.suspend(jwt.getSubject(), id, request.reason()));
  }

  /** POST /v1/admin/users/:id/reactivate — LLFR-ADMIN-02.4. */
  @POST
  @Path("/{id}/reactivate")
  @RolesAllowed({"super-admin", "moderator"})
  @Operation(summary = "Reactivate a suspended user")
  @APIResponse(responseCode = "200", description = "User reactivated")
  @APIResponse(responseCode = "404", description = "User not found")
  @APIResponse(responseCode = "409", description = "Not suspended")
  public AdminUserRowDto reactivate(@PathParam("id") String id) {
    return AdminUserRowDto.from(reactivateUser.reactivate(jwt.getSubject(), id));
  }

  /** POST /v1/admin/users/:id/impersonate — LLFR-ADMIN-02.5. Super-admin ONLY. */
  @POST
  @Path("/{id}/impersonate")
  @RolesAllowed("super-admin")
  @Operation(summary = "Issue a scoped, time-boxed impersonation token")
  @APIResponse(responseCode = "200", description = "Impersonation token issued")
  @APIResponse(responseCode = "403", description = "Requires super-admin")
  @APIResponse(responseCode = "404", description = "User not found")
  public ImpersonationTokenDto impersonate(@PathParam("id") String id) {
    return ImpersonationTokenDto.from(impersonateUser.impersonate(jwt.getSubject(), id));
  }

  /** POST /v1/admin/users/:id/data-export — LLFR-ADMIN-02.6. */
  @POST
  @Path("/{id}/data-export")
  @RolesAllowed({"super-admin", "support"})
  @Operation(summary = "Enqueue a DSAR data export job")
  @APIResponse(responseCode = "202", description = "Export job queued")
  @APIResponse(responseCode = "404", description = "User not found")
  public Response dataExport(@PathParam("id") String id) {
    DataExportJobRefDto dto = DataExportJobRefDto.from(exportUserData.export(jwt.getSubject(), id));
    return Response.status(202).entity(dto).build();
  }

  /** Suspend request body: {@code { reason } } — required, 422 if blank/missing. */
  public record SuspendRequest(@NotBlank String reason) {}
}
