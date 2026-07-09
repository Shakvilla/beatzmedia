package org.shakvilla.beatzmedia.studio.adapter.in.rest;

import java.math.BigDecimal;
import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
import org.shakvilla.beatzmedia.studio.application.port.in.GetStudioSettings;
import org.shakvilla.beatzmedia.studio.application.port.in.NotificationsView;
import org.shakvilla.beatzmedia.studio.application.port.in.PayoutSettingsView;
import org.shakvilla.beatzmedia.studio.application.port.in.PrivacySettingsView;
import org.shakvilla.beatzmedia.studio.application.port.in.SaveStudioSettings;
import org.shakvilla.beatzmedia.studio.application.port.in.SaveStudioSettingsCommand;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioDefaultsView;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioSettingsView;
import org.shakvilla.beatzmedia.studio.application.port.in.TeamMemberView;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * Thin REST resource for the Studio settings get/save endpoints (LLFR-STUDIO-04.2). Maps HTTP to
 * input ports; no business logic here. Studio ADD §5.1 / §16.
 *
 * <ul>
 *   <li>GET /v1/studio/settings → 200 {@link StudioSettingsView} — always the caller's own
 *       settings (JWT subject, no path param); never 404s.
 *   <li>PUT /v1/studio/settings → 200 {@link StudioSettingsView}; 422 {@code VALIDATION}. Only the
 *       Category A writable subset is accepted ({@code notifications}, {@code defaults}, {@code
 *       payouts}, {@code privacy}, {@code team}) — Category B fields (email, sessions,
 *       connectedApps, verification, billing, etc.) are never accepted, see studio.md §16.
 * </ul>
 *
 * <p>Auth: every endpoint requires {@code roles ∋ artist} (else 403), enforced with {@code
 * @RolesAllowed} — same mechanism as every other Studio resource ({@code StudioProfileResource},
 * {@code StudioAnalyticsResource}).
 *
 * <p><strong>IDOR — hard security requirement.</strong> The caller's artist id is resolved
 * EXCLUSIVELY from {@code jwt.getSubject()}. There is no {@code artistId} path or query parameter
 * on either endpoint — a fan or a different artist's JWT can never read or mutate another artist's
 * settings through this resource.
 */
@Path("/v1/studio/settings")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("artist")
public class StudioSettingsResource {

  private final GetStudioSettings getStudioSettings;
  private final SaveStudioSettings saveStudioSettings;
  private final JsonWebToken jwt;

  @Inject
  public StudioSettingsResource(
      GetStudioSettings getStudioSettings, SaveStudioSettings saveStudioSettings, JsonWebToken jwt) {
    this.getStudioSettings = getStudioSettings;
    this.saveStudioSettings = saveStudioSettings;
    this.jwt = jwt;
  }

  /** GET /v1/studio/settings — LLFR-STUDIO-04.2. */
  @GET
  public StudioSettingsView get() {
    return getStudioSettings.get(artistId());
  }

  /** PUT /v1/studio/settings — LLFR-STUDIO-04.2. */
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public StudioSettingsView save(@Valid SaveStudioSettingsBody body) {
    return saveStudioSettings.save(artistId(), toCommand(body));
  }

  private ArtistId artistId() {
    return new ArtistId(jwt.getSubject());
  }

  private static SaveStudioSettingsCommand toCommand(SaveStudioSettingsBody body) {
    return new SaveStudioSettingsCommand(
        body.notifications() != null
            ? new NotificationsView(
                body.notifications().sales(), body.notifications().tips(),
                body.notifications().followers(), body.notifications().payouts(),
                body.notifications().weeklySummary(), body.notifications().comments(),
                body.notifications().marketing())
            : new NotificationsView(false, false, false, false, false, false, false),
        body.defaults() != null
            ? new StudioDefaultsView(
                body.defaults().trackPrice(), body.defaults().releaseVisibility(),
                body.defaults().autoExplicit(), body.defaults().allowOffers())
            : new StudioDefaultsView(BigDecimal.ZERO, "public", false, false),
        body.payouts() != null
            ? new PayoutSettingsView(
                body.payouts().autoWithdraw(), body.payouts().autoWithdrawThreshold(),
                body.payouts().taxId())
            : new PayoutSettingsView(false, BigDecimal.ZERO, ""),
        body.privacy() != null
            ? new PrivacySettingsView(
                body.privacy().discoverable(), body.privacy().showRealName(),
                body.privacy().acceptBookings(), body.privacy().allowDms())
            : new PrivacySettingsView(false, false, false, false),
        body.team() != null
            ? body.team().stream()
                .map(t -> new TeamMemberView(t.id(), t.name(), t.email(), t.role()))
                .toList()
            : List.of());
  }

  // ---- Request DTOs (records) ----

  /**
   * {@code SaveStudioSettingsDto} — Studio ADD §6 / §16. The writable SUBSET of {@code
   * StudioSettingsDto}: Category A only. Category B fields (email, sessions, connectedApps,
   * verification, billing, twoFactor, phone, language, timezone, country) are not accepted here.
   */
  public record SaveStudioSettingsBody(
      @Valid NotificationsBody notifications,
      @Valid DefaultsBody defaults,
      @Valid PayoutsBody payouts,
      @Valid PrivacyBody privacy,
      @Valid List<TeamMemberBody> team) {}

  public record NotificationsBody(
      boolean sales,
      boolean tips,
      boolean followers,
      boolean payouts,
      boolean weeklySummary,
      boolean comments,
      boolean marketing) {}

  public record DefaultsBody(
      @DecimalMin(value = "0", inclusive = true, message = "trackPrice must not be negative.")
      BigDecimal trackPrice,

      @Pattern(
          regexp = "^(public|scheduled)$",
          message = "releaseVisibility must be 'public' or 'scheduled'.")
      String releaseVisibility,

      boolean autoExplicit,
      boolean allowOffers) {}

  public record PayoutsBody(
      boolean autoWithdraw,

      @DecimalMin(value = "0", inclusive = true, message = "autoWithdrawThreshold must not be negative.")
      BigDecimal autoWithdrawThreshold,

      String taxId) {}

  public record PrivacyBody(
      boolean discoverable, boolean showRealName, boolean acceptBookings, boolean allowDms) {}

  public record TeamMemberBody(
      @NotBlank(message = "Team member id is required.") String id,

      @Size(max = 120, message = "Team member name must not exceed 120 characters.") String name,

      String email,

      @NotBlank(message = "Team member role is required.")
      @Pattern(
          regexp = "^(Owner|Manager|Label|Invited)$",
          message = "role must be one of Owner, Manager, Label, Invited.")
      String role) {}
}
