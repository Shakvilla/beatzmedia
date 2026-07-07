package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.util.List;

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
import org.shakvilla.beatzmedia.admin.application.port.in.AssignTicket;
import org.shakvilla.beatzmedia.admin.application.port.in.GetSupportTicket;
import org.shakvilla.beatzmedia.admin.application.port.in.ListSupportTickets;
import org.shakvilla.beatzmedia.admin.application.port.in.ReplyToTicket;
import org.shakvilla.beatzmedia.admin.application.port.in.ResolveTicket;
import org.shakvilla.beatzmedia.admin.application.port.in.SupportTicketDetailView;
import org.shakvilla.beatzmedia.admin.application.port.in.TicketQuery;
import org.shakvilla.beatzmedia.admin.domain.TicketStatus;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Thin REST resource for the admin support-ticket endpoints (LLFR-ADMIN-08.1). Maps DTOs to
 * commands, calls input ports, maps results to DTOs. No business logic here. Admin ADD §5.1.
 *
 * <ul>
 *   <li>GET /v1/admin/support/tickets?status=&amp;q= → 200 {@code SupportTicket[]} (bare array,
 *       full thread per item — matches {@code admin-data.ts}'s {@code getSupportTickets()})
 *   <li>GET /v1/admin/support/tickets/:id → 200 {@code SupportTicket} (thread)
 *   <li>POST /v1/admin/support/tickets/:id/reply → 201 {@code SupportMessage}
 *   <li>POST /v1/admin/support/tickets/:id/assign → 200 {@code SupportTicket}
 *   <li>POST /v1/admin/support/tickets/:id/resolve → 200 {@code SupportTicket}
 * </ul>
 *
 * <p><strong>RBAC (admin ADD §8).</strong> Support is {@code RW} for every admin role — inbound
 * {@code @RolesAllowed} accepts all five; the application layer does not additionally narrow
 * (support has no super-admin-only action, unlike settings/compliance).
 */
@Path("/v1/admin/support/tickets")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"super-admin", "finance", "moderator", "editor", "support"})
@Tag(name = "admin-support")
@SecurityRequirement(name = "BearerAuth")
public class AdminSupportResource {

  private final ListSupportTickets listSupportTickets;
  private final GetSupportTicket getSupportTicket;
  private final ReplyToTicket replyToTicket;
  private final AssignTicket assignTicket;
  private final ResolveTicket resolveTicket;
  private final JsonWebToken jwt;

  @Inject
  public AdminSupportResource(
      ListSupportTickets listSupportTickets,
      GetSupportTicket getSupportTicket,
      ReplyToTicket replyToTicket,
      AssignTicket assignTicket,
      ResolveTicket resolveTicket,
      JsonWebToken jwt) {
    this.listSupportTickets = listSupportTickets;
    this.getSupportTicket = getSupportTicket;
    this.replyToTicket = replyToTicket;
    this.assignTicket = assignTicket;
    this.resolveTicket = resolveTicket;
    this.jwt = jwt;
  }

  /** GET /v1/admin/support/tickets — LLFR-ADMIN-08.1. Bare array, newest first. */
  @GET
  @Operation(summary = "List support tickets (inbox)")
  @APIResponse(responseCode = "200", description = "Ticket inbox with full message threads")
  public List<SupportTicketDto> list(
      @Parameter(description = "Filter by ticket status (open|pending|resolved)")
          @QueryParam("status")
          String statusParam,
      @Parameter(description = "Free-text match on subject/requester") @QueryParam("q") String q,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("100") int size) {
    TicketStatus status = statusParam == null || statusParam.isBlank()
        ? null
        : TicketStatus.fromWireValue(statusParam);
    TicketQuery query = new TicketQuery(status, q);
    Page<SupportTicketDetailView> result =
        listSupportTickets.list(jwt.getSubject(), query, new PageRequest(page, size));
    return result.items().stream().map(SupportTicketDto::from).toList();
  }

  /** GET /v1/admin/support/tickets/:id — LLFR-ADMIN-08.1 (thread). */
  @GET
  @Path("/{id}")
  @Operation(summary = "Get a support ticket with its message thread")
  @APIResponse(responseCode = "200", description = "Ticket detail")
  @APIResponse(responseCode = "404", description = "Ticket not found")
  public SupportTicketDto get(@PathParam("id") String id) {
    return SupportTicketDto.from(getSupportTicket.get(jwt.getSubject(), id));
  }

  /** POST /v1/admin/support/tickets/:id/reply — LLFR-ADMIN-08.1. */
  @POST
  @Path("/{id}/reply")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Reply to a support ticket")
  @APIResponse(responseCode = "201", description = "Reply recorded")
  @APIResponse(responseCode = "422", description = "Blank reply text")
  @APIResponse(responseCode = "404", description = "Ticket not found")
  public Response reply(@PathParam("id") String id, @Valid ReplyRequest request) {
    SupportMessageDto dto = SupportMessageDto.from(
        replyToTicket.reply(jwt.getSubject(), id, request == null ? null : request.text()));
    return Response.status(Response.Status.CREATED).entity(dto).build();
  }

  /** POST /v1/admin/support/tickets/:id/assign — LLFR-ADMIN-08.1. */
  @POST
  @Path("/{id}/assign")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Assign a support ticket to an admin team member")
  @APIResponse(responseCode = "200", description = "Ticket assigned")
  @APIResponse(responseCode = "404", description = "Ticket not found")
  public SupportTicketDto assign(@PathParam("id") String id, @Valid AssignRequest request) {
    return SupportTicketDto.from(
        assignTicket.assign(jwt.getSubject(), id, request.assigneeId()));
  }

  /** POST /v1/admin/support/tickets/:id/resolve — LLFR-ADMIN-08.1. */
  @POST
  @Path("/{id}/resolve")
  @Operation(summary = "Resolve a support ticket")
  @APIResponse(responseCode = "200", description = "Ticket resolved")
  @APIResponse(responseCode = "404", description = "Ticket not found")
  @APIResponse(responseCode = "409", description = "Ticket already resolved")
  public SupportTicketDto resolve(@PathParam("id") String id) {
    return SupportTicketDto.from(resolveTicket.resolve(jwt.getSubject(), id));
  }

  /** Reply request body: {@code { text }}. */
  public record ReplyRequest(@NotBlank String text) {}

  /** Assign request body: {@code { assigneeId }}. */
  public record AssignRequest(@NotBlank String assigneeId) {}
}
