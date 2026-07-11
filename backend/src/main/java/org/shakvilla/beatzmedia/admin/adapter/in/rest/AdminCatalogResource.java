package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.time.Instant;
import java.util.Optional;

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

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.shakvilla.beatzmedia.admin.application.port.in.ApproveCatalogItem;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogQuery;
import org.shakvilla.beatzmedia.admin.application.port.in.FlagCatalogItem;
import org.shakvilla.beatzmedia.admin.application.port.in.GetCatalogItem;
import org.shakvilla.beatzmedia.admin.application.port.in.ListCatalogModeration;
import org.shakvilla.beatzmedia.admin.application.port.in.ReinstateCatalogItem;
import org.shakvilla.beatzmedia.admin.application.port.in.TakedownCatalogItem;
import org.shakvilla.beatzmedia.admin.domain.CatalogFilter;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Thin REST resource for the admin catalog-moderation endpoints (LLFR-ADMIN-03.1/.2). Maps DTOs
 * to input-port calls; no business logic here. Admin ADD §5.1.
 *
 * <ul>
 *   <li>GET /v1/admin/catalog?status=pending|published|takedown&amp;q=&amp;page= → 200 {@link
 *       PagedCatalogDto} (super-admin, moderator, support)
 *   <li>GET /v1/admin/catalog/:id → 200 {@link CatalogItemDetailDto}; 404 if not found
 *   <li>POST /v1/admin/catalog/:id/approve { goLiveAt? } → 200 {@link CatalogItemDetailDto}
 *       (super-admin, moderator)
 *   <li>POST /v1/admin/catalog/:id/flag { note? } → 200 {@link CatalogItemDetailDto} (super-admin,
 *       moderator)
 *   <li>POST /v1/admin/catalog/:id/takedown { reason } → 200 {@link CatalogItemDetailDto}
 *       (super-admin, moderator)
 *   <li>POST /v1/admin/catalog/:id/reinstate → 200 {@link CatalogItemDetailDto} (super-admin,
 *       moderator) — additive; relocated here from catalog's temporary {@code AdminCatalogResource}
 *       placeholder (catalog ADD §5.1's WU-CAT-4 note), not in admin ADD §5.1's illustrative table
 * </ul>
 *
 * <p><strong>Relocation note (WU-ADM-3).</strong> {@code approve}/{@code takedown}/{@code
 * reinstate} previously lived in {@code catalog.adapter.in.rest.AdminCatalogResource} as a
 * documented temporary placeholder ("since no separate admin REST module exists yet"); this
 * resource is their permanent home. The underlying {@code catalog.PublishRelease} FSM is
 * unchanged and self-audits every transition — this resource/its services append no second
 * AuditEntry for those three actions (INV-10 "exactly one"). {@code flag} is admin-owned (creates
 * a {@code ModerationCase}, admin audits it itself) — see admin ADD §13 (WU-ADM-3 as-built).
 *
 * <p><strong>Actor resolution.</strong> Every mutation's actor is {@code jwt.getSubject()} only —
 * never a body/path parameter (IDOR prevention, established convention). <strong>RBAC (admin ADD
 * §8).</strong> Reads accept super-admin/moderator/support; writes accept super-admin/moderator
 * only (support is read-only for catalog moderation).
 */
@Path("/v1/admin/catalog")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "admin-catalog")
@SecurityRequirement(name = "BearerAuth")
public class AdminCatalogResource {

  private final ListCatalogModeration listCatalogModeration;
  private final GetCatalogItem getCatalogItem;
  private final ApproveCatalogItem approveCatalogItem;
  private final FlagCatalogItem flagCatalogItem;
  private final TakedownCatalogItem takedownCatalogItem;
  private final ReinstateCatalogItem reinstateCatalogItem;
  private final JsonWebToken jwt;

  @Inject
  public AdminCatalogResource(
      ListCatalogModeration listCatalogModeration,
      GetCatalogItem getCatalogItem,
      ApproveCatalogItem approveCatalogItem,
      FlagCatalogItem flagCatalogItem,
      TakedownCatalogItem takedownCatalogItem,
      ReinstateCatalogItem reinstateCatalogItem,
      JsonWebToken jwt) {
    this.listCatalogModeration = listCatalogModeration;
    this.getCatalogItem = getCatalogItem;
    this.approveCatalogItem = approveCatalogItem;
    this.flagCatalogItem = flagCatalogItem;
    this.takedownCatalogItem = takedownCatalogItem;
    this.reinstateCatalogItem = reinstateCatalogItem;
    this.jwt = jwt;
  }

  /** GET /v1/admin/catalog?status=&q=&page=&size= — LLFR-ADMIN-03.1. */
  @GET
  @RolesAllowed({"super-admin", "moderator", "support"})
  @Operation(summary = "List/search catalog releases for moderation")
  @APIResponse(responseCode = "200", description = "Paged catalog list with counts")
  @APIResponse(responseCode = "422", description = "Unrecognised status filter value")
  public PagedCatalogDto list(
      @Parameter(description = "pending|published|takedown") @QueryParam("status") String statusParam,
      @Parameter(description = "Free-text match on title/artist") @QueryParam("q") String q,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("20") int size) {
    CatalogFilter filter = CatalogFilter.fromWireValue(statusParam);
    return PagedCatalogDto.from(
        listCatalogModeration.list(new CatalogQuery(filter, q), new PageRequest(page, size)));
  }

  /** GET /v1/admin/catalog/:id — LLFR-ADMIN-03.1. */
  @GET
  @Path("/{id}")
  @RolesAllowed({"super-admin", "moderator", "support"})
  @Operation(summary = "Get a release's catalog-moderation detail")
  @APIResponse(responseCode = "200", description = "Catalog item detail")
  @APIResponse(responseCode = "404", description = "Release not found")
  public CatalogItemDetailDto get(@PathParam("id") String id) {
    return CatalogItemDetailDto.from(getCatalogItem.get(id));
  }

  /** POST /v1/admin/catalog/:id/approve { goLiveAt? } — LLFR-ADMIN-03.2. */
  @POST
  @Path("/{id}/approve")
  @RolesAllowed({"super-admin", "moderator"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Approve a release (immediate or scheduled go-live)")
  @APIResponse(responseCode = "200", description = "Release approved")
  @APIResponse(responseCode = "409", description = "Illegal transition")
  public CatalogItemDetailDto approve(@PathParam("id") String id, ApproveRequest request) {
    Optional<Instant> goLiveAt = parseInstant(request != null ? request.goLiveAt() : null);
    return CatalogItemDetailDto.from(approveCatalogItem.approve(jwt.getSubject(), id, goLiveAt));
  }

  /** POST /v1/admin/catalog/:id/flag { note? } — LLFR-ADMIN-03.2. */
  @POST
  @Path("/{id}/flag")
  @RolesAllowed({"super-admin", "moderator"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Flag a release for moderation review")
  @APIResponse(responseCode = "200", description = "Release flagged; moderation case opened")
  @APIResponse(responseCode = "404", description = "Release not found")
  public CatalogItemDetailDto flag(@PathParam("id") String id, FlagRequest request) {
    Optional<String> note = Optional.ofNullable(request != null ? request.note() : null)
        .filter(n -> !n.isBlank());
    return CatalogItemDetailDto.from(flagCatalogItem.flag(jwt.getSubject(), id, note));
  }

  /** POST /v1/admin/catalog/:id/takedown { reason } — LLFR-ADMIN-03.2. */
  @POST
  @Path("/{id}/takedown")
  @RolesAllowed({"super-admin", "moderator"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Take a release down")
  @APIResponse(responseCode = "200", description = "Release taken down")
  @APIResponse(responseCode = "422", description = "Blank/missing reason")
  @APIResponse(responseCode = "409", description = "Illegal transition")
  public CatalogItemDetailDto takedown(@PathParam("id") String id, @Valid TakedownRequest request) {
    return CatalogItemDetailDto.from(
        takedownCatalogItem.takedown(jwt.getSubject(), id, request.reason()));
  }

  /** POST /v1/admin/catalog/:id/reinstate — additive (see class javadoc). */
  @POST
  @Path("/{id}/reinstate")
  @RolesAllowed({"super-admin", "moderator"})
  @Operation(summary = "Reinstate a taken-down release back to live")
  @APIResponse(responseCode = "200", description = "Release reinstated")
  @APIResponse(responseCode = "409", description = "Illegal transition")
  public CatalogItemDetailDto reinstate(@PathParam("id") String id) {
    return CatalogItemDetailDto.from(reinstateCatalogItem.reinstate(jwt.getSubject(), id));
  }

  private static Optional<Instant> parseInstant(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Instant.parse(value));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  /** Approve request body: {@code { goLiveAt? } } — ISO-8601; absent/past → immediate go-live. */
  public record ApproveRequest(String goLiveAt) {}

  /** Flag request body: {@code { note? } }. */
  public record FlagRequest(String note) {}

  /** Takedown request body: {@code { reason } } — required, 422 if blank/missing. */
  public record TakedownRequest(@NotBlank String reason) {}
}
