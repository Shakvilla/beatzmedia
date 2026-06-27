package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.identity.application.port.in.ChangeAdminRole;
import org.shakvilla.beatzmedia.identity.application.port.in.InviteAdmin;
import org.shakvilla.beatzmedia.identity.application.port.in.ListAdminTeam;
import org.shakvilla.beatzmedia.identity.application.port.in.RemoveAdmin;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AdminRole;

/**
 * Thin REST resource for the admin-team endpoints. Maps DTOs to commands, calls input ports, maps
 * results to DTOs. No business logic here. Identity ADD §5.1.
 *
 * <ul>
 *   <li>GET /v1/admin/team → 200 AdminMemberDto[] (any admin role) — LLFR-IDENTITY-03.1
 *   <li>POST /v1/admin/team/invite → 201 AdminMemberDto (super-admin only) — LLFR-IDENTITY-03.2
 *   <li>PATCH /v1/admin/team/:id → 200 AdminMemberDto (super-admin only) — LLFR-IDENTITY-03.3
 *   <li>DELETE /v1/admin/team/:id → 204 (super-admin only) — LLFR-IDENTITY-03.3
 * </ul>
 *
 * <p>RBAC is enforced in TWO places (DoD §5):
 *
 * <ol>
 *   <li>Inbound: {@code @RolesAllowed} on each method.
 *   <li>Application layer: each service re-checks that the actor is a super-admin before mutating.
 * </ol>
 *
 * <p>The allowed roles are config-driven (OQ-1 default). For list: all five admin roles. For
 * mutations: super-admin only. The JWT groups claim carries kebab-case role strings issued by
 * {@link org.shakvilla.beatzmedia.identity.application.service.LoginService}.
 */
@Path("/v1/admin/team")
@Produces(MediaType.APPLICATION_JSON)
public class AdminTeamResource {

  // Config-driven role sets (OQ-1). Roles loaded from application.properties via @RolesAllowed.
  // Any of these five admin roles allows the list endpoint.
  // Only super-admin is allowed to mutate.
  // These strings must match the JWT groups claim values (kebab-case).

  private final ListAdminTeam listAdminTeam;
  private final InviteAdmin inviteAdmin;
  private final ChangeAdminRole changeAdminRole;
  private final RemoveAdmin removeAdmin;
  private final JsonWebToken jwt;

  @Inject
  public AdminTeamResource(
      ListAdminTeam listAdminTeam,
      InviteAdmin inviteAdmin,
      ChangeAdminRole changeAdminRole,
      RemoveAdmin removeAdmin,
      JsonWebToken jwt) {
    this.listAdminTeam = listAdminTeam;
    this.inviteAdmin = inviteAdmin;
    this.changeAdminRole = changeAdminRole;
    this.removeAdmin = removeAdmin;
    this.jwt = jwt;
  }

  /**
   * GET /v1/admin/team — LLFR-IDENTITY-03.1. Auth: any admin role. Returns all admin members
   * ordered by most recently active.
   */
  @GET
  @RolesAllowed({"super-admin", "finance", "moderator", "editor", "support"})
  public List<AdminMemberDto> listTeam() {
    return listAdminTeam.list().stream()
        .map(AdminMemberDto::from)
        .toList();
  }

  /**
   * POST /v1/admin/team/invite — LLFR-IDENTITY-03.2. Auth: super-admin only. Returns 201 with the
   * new AdminMemberDto.
   */
  @POST
  @Path("/invite")
  @RolesAllowed("super-admin")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response invite(@Valid InviteRequest request) {
    AccountId actor = new AccountId(jwt.getSubject());
    AdminRole role = AdminRole.fromWireValue(request.role()); // throws InvalidRoleException → 422
    AdminMemberDto dto = AdminMemberDto.from(
        inviteAdmin.invite(actor, new InviteAdmin.InviteAdminCommand(request.email(), role)));
    return Response.status(Response.Status.CREATED).entity(dto).build();
  }

  /**
   * PATCH /v1/admin/team/:id — LLFR-IDENTITY-03.3. Auth: super-admin only. Returns 200 with the
   * updated AdminMemberDto.
   */
  @PATCH
  @Path("/{id}")
  @RolesAllowed("super-admin")
  @Consumes(MediaType.APPLICATION_JSON)
  public AdminMemberDto changeRole(@PathParam("id") String id, @Valid RoleChangeRequest request) {
    AccountId actor = new AccountId(jwt.getSubject());
    AdminRole role = AdminRole.fromWireValue(request.role()); // throws InvalidRoleException → 422
    return AdminMemberDto.from(changeAdminRole.changeRole(actor, id, role));
  }

  /**
   * DELETE /v1/admin/team/:id — LLFR-IDENTITY-03.3. Auth: super-admin only. Returns 204 on
   * success.
   */
  @DELETE
  @Path("/{id}")
  @RolesAllowed("super-admin")
  public Response removeTeamMember(@PathParam("id") String id) {
    AccountId actor = new AccountId(jwt.getSubject());
    removeAdmin.remove(actor, id);
    return Response.noContent().build();
  }
}
