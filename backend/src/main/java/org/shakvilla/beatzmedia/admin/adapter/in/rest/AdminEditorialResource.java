package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.time.Instant;
import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.shakvilla.beatzmedia.admin.application.port.in.CreateCuratedPlaylist;
import org.shakvilla.beatzmedia.admin.application.port.in.ListCuratedPlaylists;
import org.shakvilla.beatzmedia.admin.application.port.in.ListFeaturedSlots;
import org.shakvilla.beatzmedia.admin.application.port.in.ListPushItems;
import org.shakvilla.beatzmedia.admin.application.port.in.SaveFeaturedSlots;
import org.shakvilla.beatzmedia.admin.application.port.in.SchedulePushItem;
import org.shakvilla.beatzmedia.admin.domain.CuratedPlaylist;
import org.shakvilla.beatzmedia.admin.domain.FeaturedSlot;
import org.shakvilla.beatzmedia.admin.domain.PushItem;

/**
 * Thin REST resource for the admin editorial endpoints (LLFR-ADMIN-06.1). Maps DTOs to commands,
 * calls input ports, maps results to DTOs. No business logic here. Admin ADD §5.1.
 *
 * <ul>
 *   <li>GET /v1/admin/editorial/featured → 200 {@code FeaturedSlot[]}
 *   <li>PUT /v1/admin/editorial/featured → 200 {@code FeaturedSlot[]} (ordered full-set replace)
 *   <li>GET /v1/admin/editorial/push → 200 {@code PushItem[]}
 *   <li>POST /v1/admin/editorial/push → 201 {@code PushItem}
 *   <li>GET /v1/admin/editorial/playlists → 200 {@code CuratedPlaylist[]}
 *   <li>POST /v1/admin/editorial/playlists → 201 {@code CuratedPlaylist}
 * </ul>
 *
 * <p><strong>RBAC (admin ADD §8).</strong> Editorial is {@code RW} for super-admin and editor,
 * {@code R} for support; finance/moderator have no access. Reads accept {@code super-admin,
 * editor, support}; writes accept only {@code super-admin, editor} — enforced per-method since the
 * matrix differs between GET and PUT/POST (unlike support, which is uniformly RW for all roles).
 */
@Path("/v1/admin/editorial")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "admin-editorial")
@SecurityRequirement(name = "BearerAuth")
public class AdminEditorialResource {

  private static final String[] READ_ROLES = {"super-admin", "editor", "support"};
  private static final String[] WRITE_ROLES = {"super-admin", "editor"};

  private final ListFeaturedSlots listFeaturedSlots;
  private final SaveFeaturedSlots saveFeaturedSlots;
  private final ListPushItems listPushItems;
  private final SchedulePushItem schedulePushItem;
  private final ListCuratedPlaylists listCuratedPlaylists;
  private final CreateCuratedPlaylist createCuratedPlaylist;
  private final JsonWebToken jwt;

  @Inject
  public AdminEditorialResource(
      ListFeaturedSlots listFeaturedSlots,
      SaveFeaturedSlots saveFeaturedSlots,
      ListPushItems listPushItems,
      SchedulePushItem schedulePushItem,
      ListCuratedPlaylists listCuratedPlaylists,
      CreateCuratedPlaylist createCuratedPlaylist,
      JsonWebToken jwt) {
    this.listFeaturedSlots = listFeaturedSlots;
    this.saveFeaturedSlots = saveFeaturedSlots;
    this.listPushItems = listPushItems;
    this.schedulePushItem = schedulePushItem;
    this.listCuratedPlaylists = listCuratedPlaylists;
    this.createCuratedPlaylist = createCuratedPlaylist;
    this.jwt = jwt;
  }

  /** GET /v1/admin/editorial/featured — LLFR-ADMIN-06.1. */
  @GET
  @Path("/featured")
  @RolesAllowed({"super-admin", "editor", "support"})
  @Operation(summary = "List ordered home-featured slots")
  @APIResponse(responseCode = "200", description = "Featured slots ordered by position")
  public List<FeaturedSlotDto> listFeatured() {
    return listFeaturedSlots.list().stream().map(FeaturedSlotDto::from).toList();
  }

  /** PUT /v1/admin/editorial/featured — LLFR-ADMIN-06.1 (ordered full-set replace). */
  @PUT
  @Path("/featured")
  @RolesAllowed({"super-admin", "editor"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Replace the ordered home-featured slots")
  @APIResponse(responseCode = "200", description = "Featured slots saved")
  @APIResponse(responseCode = "422", description = "Invalid payload (blank title or duplicate id)")
  public List<FeaturedSlotDto> saveFeatured(@Valid List<FeaturedSlotRequest> body) {
    List<SaveFeaturedSlots.FeaturedSlotInput> ordered = body.stream()
        .map(r -> new SaveFeaturedSlots.FeaturedSlotInput(r.id(), r.title(), r.note(), r.sponsored()))
        .toList();
    List<FeaturedSlot> saved = saveFeaturedSlots.save(jwt.getSubject(), ordered);
    return saved.stream().map(FeaturedSlotDto::from).toList();
  }

  /** GET /v1/admin/editorial/push — LLFR-ADMIN-06.1. */
  @GET
  @Path("/push")
  @RolesAllowed({"super-admin", "editor", "support"})
  @Operation(summary = "List the scheduled push notification calendar")
  @APIResponse(responseCode = "200", description = "Scheduled push items")
  public List<PushItemDto> listPush() {
    return listPushItems.list().stream().map(PushItemDto::from).toList();
  }

  /** POST /v1/admin/editorial/push — LLFR-ADMIN-06.1. */
  @POST
  @Path("/push")
  @RolesAllowed({"super-admin", "editor"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Schedule a new push notification")
  @APIResponse(responseCode = "201", description = "Push item scheduled")
  @APIResponse(responseCode = "422", description = "Blank required field")
  public Response schedulePush(@Valid PushItemRequest request) {
    Instant scheduledAt = request.scheduledAt() == null || request.scheduledAt().isBlank()
        ? null
        : Instant.parse(request.scheduledAt());
    PushItem item = schedulePushItem.schedule(
        jwt.getSubject(),
        new SchedulePushItem.PushItemInput(
            request.day(), request.timeLabel(), request.title(), request.audience(), scheduledAt));
    return Response.status(Response.Status.CREATED).entity(PushItemDto.from(item)).build();
  }

  /** GET /v1/admin/editorial/playlists — LLFR-ADMIN-06.1. */
  @GET
  @Path("/playlists")
  @RolesAllowed({"super-admin", "editor", "support"})
  @Operation(summary = "List curated playlists")
  @APIResponse(responseCode = "200", description = "Curated playlists")
  public List<CuratedPlaylistDto> listPlaylists() {
    return listCuratedPlaylists.list().stream().map(CuratedPlaylistDto::from).toList();
  }

  /** POST /v1/admin/editorial/playlists — LLFR-ADMIN-06.1. */
  @POST
  @Path("/playlists")
  @RolesAllowed({"super-admin", "editor"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Create a curated playlist")
  @APIResponse(responseCode = "201", description = "Curated playlist created")
  @APIResponse(responseCode = "422", description = "Blank name")
  public Response createPlaylist(@Valid CuratedPlaylistRequest request) {
    CuratedPlaylist playlist = createCuratedPlaylist.create(
        jwt.getSubject(), new CreateCuratedPlaylist.CuratedPlaylistInput(request.name()));
    return Response.status(Response.Status.CREATED).entity(CuratedPlaylistDto.from(playlist)).build();
  }

  /** PUT /featured item request body: {@code { id?, title, note?, sponsored? }}. */
  public record FeaturedSlotRequest(String id, @NotBlank String title, String note, boolean sponsored) {}

  /** POST /push request body: {@code { day, timeLabel, title, audience, scheduledAt? }}. */
  public record PushItemRequest(
      @NotBlank String day,
      @NotBlank String timeLabel,
      @NotBlank String title,
      @NotBlank String audience,
      String scheduledAt) {}

  /** POST /playlists request body: {@code { name }}. */
  public record CuratedPlaylistRequest(@NotBlank String name) {}
}
