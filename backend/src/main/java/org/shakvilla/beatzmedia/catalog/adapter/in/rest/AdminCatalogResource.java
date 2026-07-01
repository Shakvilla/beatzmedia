package org.shakvilla.beatzmedia.catalog.adapter.in.rest;

import java.time.Instant;
import java.util.Optional;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.catalog.application.port.in.PublishRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.PublishRelease.ReleaseTransition;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseView;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/**
 * Inbound REST adapter for admin-driven release moderation (LLFR-CATALOG-02.5). Thin: DTO in →
 * command → {@link PublishRelease} input port → DTO out. No business logic here. Catalog ADD §5.1
 * / §9 — RBAC: {@code moderator} or {@code editor} admin scope (matching the RBAC config
 * established by WU-IDN-4). Every transition here is a privileged mutation; the underlying
 * {@code PublishReleaseService} appends exactly one {@code AuditEntry} per call (INV-10).
 *
 * <ul>
 *   <li>POST /v1/admin/catalog/:id/approve — {@code {date?: ISO-8601}}. With a future {@code date},
 *       {@code in_review -> scheduled}; without a date (or a date not in the future), {@code
 *       in_review -> live} immediately.
 *   <li>POST /v1/admin/catalog/:id/takedown — {@code live -> takedown}.
 *   <li>POST /v1/admin/catalog/:id/reinstate — {@code takedown -> live}.
 * </ul>
 *
 * <p>Illegal transitions return 409 {@code ILLEGAL_TRANSITION} (mapped by
 * {@link org.shakvilla.beatzmedia.platform.adapter.in.rest.DomainExceptionMapper}).
 */
@Path("/v1/admin/catalog")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"moderator", "editor"})
public class AdminCatalogResource {

  private final PublishRelease publishRelease;
  private final JsonWebToken jwt;

  @Inject
  public AdminCatalogResource(PublishRelease publishRelease, JsonWebToken jwt) {
    this.publishRelease = publishRelease;
    this.jwt = jwt;
  }

  /**
   * POST /v1/admin/catalog/:id/approve — LLFR-CATALOG-02.5. A future {@code date} yields
   * {@code scheduled}; an absent/non-future {@code date} yields an immediate {@code live}.
   */
  @POST
  @Path("/{id}/approve")
  @Consumes(MediaType.APPLICATION_JSON)
  public StudioReleaseView approve(@PathParam("id") String id, ApproveRequest body) {
    Optional<Instant> date = parseDate(body != null ? body.date() : null);
    ReleaseTransition action =
        date.isPresent() ? ReleaseTransition.APPROVE_SCHEDULED : ReleaseTransition.APPROVE_IMMEDIATE;
    return publishRelease.transition(new ReleaseId(id), action, actorId(), date);
  }

  /** POST /v1/admin/catalog/:id/takedown — LLFR-CATALOG-02.5. Requires {@code { reason }}. */
  @POST
  @Path("/{id}/takedown")
  @Consumes(MediaType.APPLICATION_JSON)
  public StudioReleaseView takedown(@PathParam("id") String id, TakedownRequest body) {
    String reason = body != null ? body.reason() : null;
    return publishRelease.transition(
        new ReleaseId(id), ReleaseTransition.TAKEDOWN, actorId(), Optional.empty(), reason);
  }

  /** POST /v1/admin/catalog/:id/reinstate — LLFR-CATALOG-02.5. */
  @POST
  @Path("/{id}/reinstate")
  public StudioReleaseView reinstate(@PathParam("id") String id) {
    return publishRelease.transition(
        new ReleaseId(id), ReleaseTransition.REINSTATE, actorId(), Optional.empty());
  }

  private String actorId() {
    return jwt.getSubject();
  }

  private Optional<Instant> parseDate(String date) {
    if (date == null || date.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Instant.parse(date));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  // ---- DTO types ----

  public record ApproveRequest(String date) {}

  public record TakedownRequest(String reason) {}
}
