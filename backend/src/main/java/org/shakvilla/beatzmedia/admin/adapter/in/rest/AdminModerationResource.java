package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.util.Optional;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.shakvilla.beatzmedia.admin.application.port.in.GetModerationQueue;
import org.shakvilla.beatzmedia.admin.application.port.in.ModQuery;
import org.shakvilla.beatzmedia.admin.application.port.in.ModerationActions;
import org.shakvilla.beatzmedia.admin.domain.ModReason;
import org.shakvilla.beatzmedia.admin.domain.ModStatus;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Thin REST resource for the moderation-queue endpoints (LLFR-ADMIN-04.1). Maps DTOs to
 * input-port calls; no business logic here. Admin ADD §5.1.
 *
 * <ul>
 *   <li>GET /v1/admin/moderation?status=&amp;type= → 200 {@link ModerationQueueDto} (+ SLA/
 *       escalation summary; super-admin, moderator, support)
 *   <li>POST /v1/admin/moderation/:id/review → 200 {@link ModerationCaseDto} (super-admin, moderator)
 *   <li>POST /v1/admin/moderation/:id/approve → 200 {@link ModerationCaseDto} (super-admin, moderator)
 *   <li>POST /v1/admin/moderation/:id/remove { reason? } → 200 {@link ModerationCaseDto}
 *       (super-admin, moderator)
 *   <li>POST /v1/admin/moderation/:id/escalate → 200 {@link ModerationCaseDto} (super-admin, moderator)
 *   <li>POST /v1/admin/moderation/:id/dismiss → 200 {@link ModerationCaseDto} (super-admin, moderator)
 * </ul>
 *
 * <p><strong>Actor resolution.</strong> Every mutation's actor is {@code jwt.getSubject()} only —
 * never a body/path parameter (IDOR prevention). <strong>RBAC (admin ADD §8).</strong> Reads
 * accept super-admin/moderator/support; writes accept super-admin/moderator only.
 */
@Path("/v1/admin/moderation")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "admin-moderation")
@SecurityRequirement(name = "BearerAuth")
public class AdminModerationResource {

  private final GetModerationQueue getModerationQueue;
  private final ModerationActions moderationActions;
  private final JsonWebToken jwt;

  @Inject
  public AdminModerationResource(
      GetModerationQueue getModerationQueue, ModerationActions moderationActions,
      JsonWebToken jwt) {
    this.getModerationQueue = getModerationQueue;
    this.moderationActions = moderationActions;
    this.jwt = jwt;
  }

  /** GET /v1/admin/moderation?status=&type=&page=&size= — LLFR-ADMIN-04.1. */
  @GET
  @RolesAllowed({"super-admin", "moderator", "support"})
  @Operation(summary = "List the moderation queue (+ SLA/escalation summary)")
  @APIResponse(responseCode = "200", description = "Paged moderation queue")
  @APIResponse(responseCode = "422", description = "Unrecognised status/type filter value")
  public ModerationQueueDto queue(
      @Parameter(description = "open|in_review|resolved") @QueryParam("status") String statusParam,
      @Parameter(description = "Copyright|Hate speech|Sexual content|Spam|Impersonation")
          @QueryParam("type")
          String typeParam,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("20") int size) {
    ModStatus status = ModStatus.fromWireValue(statusParam);
    ModReason type = ModReason.fromWireValue(typeParam);
    return ModerationQueueDto.from(
        getModerationQueue.queue(new ModQuery(status, type), new PageRequest(page, size)));
  }

  /** POST /v1/admin/moderation/:id/review — LLFR-ADMIN-04.1. */
  @POST
  @Path("/{id}/review")
  @RolesAllowed({"super-admin", "moderator"})
  @Operation(summary = "Mark a moderation case as being actively reviewed")
  @APIResponse(responseCode = "200", description = "Case now in_review")
  @APIResponse(responseCode = "404", description = "Case not found")
  @APIResponse(responseCode = "409", description = "Illegal transition")
  public ModerationCaseDto review(@PathParam("id") String id) {
    return ModerationCaseDto.from(moderationActions.review(jwt.getSubject(), id));
  }

  /** POST /v1/admin/moderation/:id/approve — LLFR-ADMIN-04.1 (approve & keep content). */
  @POST
  @Path("/{id}/approve")
  @RolesAllowed({"super-admin", "moderator"})
  @Operation(summary = "Approve the reported content and resolve the case")
  @APIResponse(responseCode = "200", description = "Case resolved")
  @APIResponse(responseCode = "404", description = "Case not found")
  @APIResponse(responseCode = "409", description = "Illegal transition")
  public ModerationCaseDto approve(@PathParam("id") String id) {
    return ModerationCaseDto.from(moderationActions.approve(jwt.getSubject(), id));
  }

  /** POST /v1/admin/moderation/:id/remove { reason? } — LLFR-ADMIN-04.1. */
  @POST
  @Path("/{id}/remove")
  @RolesAllowed({"super-admin", "moderator"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Remove the reported content and resolve the case")
  @APIResponse(responseCode = "200", description = "Case resolved")
  @APIResponse(responseCode = "404", description = "Case not found")
  @APIResponse(responseCode = "409", description = "Illegal transition")
  public ModerationCaseDto remove(@PathParam("id") String id, RemoveRequest request) {
    Optional<String> reason = Optional.ofNullable(request != null ? request.reason() : null)
        .filter(r -> !r.isBlank());
    return ModerationCaseDto.from(moderationActions.remove(jwt.getSubject(), id, reason));
  }

  /** POST /v1/admin/moderation/:id/escalate — LLFR-ADMIN-04.1. */
  @POST
  @Path("/{id}/escalate")
  @RolesAllowed({"super-admin", "moderator"})
  @Operation(summary = "Escalate a moderation case to senior review")
  @APIResponse(responseCode = "200", description = "Case escalated")
  @APIResponse(responseCode = "404", description = "Case not found")
  @APIResponse(responseCode = "409", description = "Illegal transition")
  public ModerationCaseDto escalate(@PathParam("id") String id) {
    return ModerationCaseDto.from(moderationActions.escalate(jwt.getSubject(), id));
  }

  /** POST /v1/admin/moderation/:id/dismiss — LLFR-ADMIN-04.1. */
  @POST
  @Path("/{id}/dismiss")
  @RolesAllowed({"super-admin", "moderator"})
  @Operation(summary = "Dismiss the report and resolve the case")
  @APIResponse(responseCode = "200", description = "Case resolved")
  @APIResponse(responseCode = "404", description = "Case not found")
  @APIResponse(responseCode = "409", description = "Illegal transition")
  public ModerationCaseDto dismiss(@PathParam("id") String id) {
    return ModerationCaseDto.from(moderationActions.dismiss(jwt.getSubject(), id));
  }

  /** Remove request body: {@code { reason? } }. */
  public record RemoveRequest(String reason) {}
}
