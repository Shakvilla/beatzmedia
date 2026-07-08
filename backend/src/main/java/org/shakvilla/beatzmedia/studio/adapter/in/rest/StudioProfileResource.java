package org.shakvilla.beatzmedia.studio.adapter.in.rest;

import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.studio.application.port.in.GetStudioProfile;
import org.shakvilla.beatzmedia.studio.application.port.in.SaveStudioProfile;
import org.shakvilla.beatzmedia.studio.application.port.in.SaveStudioProfileCommand;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioLinks;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioPressAsset;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioProfileView;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioShow;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * Thin REST resource for the Studio profile get/save endpoints (LLFR-STUDIO-01.1). Maps HTTP to
 * input ports; no business logic here — genre taxonomy / username-uniqueness validation lives
 * entirely in {@code SaveStudioProfileService}. Studio ADD §5.1 / API-CONTRACT.md.
 *
 * <ul>
 *   <li>GET /v1/studio/profile → 200 {@link StudioProfileView} — always the caller's own profile
 *       (JWT subject, no path param); never 404s.
 *   <li>PUT /v1/studio/profile → 200 {@link StudioProfileView}; 409 {@code USERNAME_TAKEN}; 422
 *       {@code INVALID_GENRE}.
 * </ul>
 *
 * <p>Auth: every endpoint requires {@code roles ∋ artist} (else 403), enforced with {@code
 * @RolesAllowed} — the same mechanism already used by {@code StudioReleaseResource} (catalog) and
 * {@code StudioPayoutsResource} (payments) for the identical "artist, own studio" requirement, so
 * this resource does not introduce a new auth mechanism.
 */
@Path("/v1/studio/profile")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("artist")
public class StudioProfileResource {

  private final GetStudioProfile getStudioProfile;
  private final SaveStudioProfile saveStudioProfile;
  private final JsonWebToken jwt;

  @Inject
  public StudioProfileResource(
      GetStudioProfile getStudioProfile, SaveStudioProfile saveStudioProfile, JsonWebToken jwt) {
    this.getStudioProfile = getStudioProfile;
    this.saveStudioProfile = saveStudioProfile;
    this.jwt = jwt;
  }

  /** GET /v1/studio/profile — LLFR-STUDIO-01.1. */
  @GET
  public StudioProfileView get() {
    return getStudioProfile.get(artistId());
  }

  /** PUT /v1/studio/profile — LLFR-STUDIO-01.1. */
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public StudioProfileView save(@Valid SaveStudioProfileBody body) {
    return saveStudioProfile.save(artistId(), toCommand(body));
  }

  private ArtistId artistId() {
    return new ArtistId(jwt.getSubject());
  }

  private static SaveStudioProfileCommand toCommand(SaveStudioProfileBody body) {
    return new SaveStudioProfileCommand(
        body.displayName(),
        body.username(),
        body.hometown(),
        body.genres() != null ? body.genres() : List.of(),
        body.bio(),
        body.avatar(),
        body.banner(),
        body.links() != null
            ? new StudioLinks(
                body.links().instagram(), body.links().twitter(), body.links().youtube(),
                body.links().website())
            : new StudioLinks("", "", "", ""),
        body.shows() != null
            ? body.shows().stream()
                .map(s -> new StudioShow(s.id(), s.venue(), s.date(), s.city()))
                .toList()
            : List.of(),
        body.featuredTrackId(),
        body.bookingEmail(),
        body.pressAssets() != null
            ? body.pressAssets().stream()
                .map(a -> new StudioPressAsset(a.id(), a.name(), a.url()))
                .toList()
            : List.of());
  }

  // ---- Request DTOs (records) ----

  /** {@code SaveStudioProfileDto} — Studio ADD §6. Whole shape is writable; no server-managed field. */
  public record SaveStudioProfileBody(
      @NotBlank(message = "Display name is required.")
      @Size(max = 120, message = "Display name must not exceed 120 characters.")
      String displayName,

      @NotBlank(message = "Username is required.")
      @Pattern(
          regexp = "^@?[A-Za-z0-9_.-]{3,30}$",
          message = "Username must be 3-30 letters, numbers, '.', '_' or '-' (an optional leading '@' is allowed).")
      String username,

      @Size(max = 120, message = "Hometown must not exceed 120 characters.")
      String hometown,

      List<String> genres,

      @Size(max = 2000, message = "Bio must not exceed 2000 characters.")
      String bio,

      String avatar,
      String banner,
      LinksBody links,
      List<ShowBody> shows,
      String featuredTrackId,

      String bookingEmail,

      List<PressAssetBody> pressAssets) {}

  public record LinksBody(String instagram, String twitter, String youtube, String website) {}

  public record ShowBody(
      @NotBlank String id, String venue, String date, String city) {}

  public record PressAssetBody(
      @NotBlank String id, String name, String url) {}
}
