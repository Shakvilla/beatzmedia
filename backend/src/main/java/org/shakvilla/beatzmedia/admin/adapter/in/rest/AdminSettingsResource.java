package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.shakvilla.beatzmedia.admin.application.port.in.GetSettings;
import org.shakvilla.beatzmedia.admin.application.port.in.PlatformSettingsInput;
import org.shakvilla.beatzmedia.admin.application.port.in.PlatformSettingsView;
import org.shakvilla.beatzmedia.admin.application.port.in.SaveSettings;

/**
 * Thin REST resource for the platform settings endpoints (LLFR-ADMIN-10.1). Admin ADD §5.1 / §12.
 *
 * <ul>
 *   <li>GET /v1/admin/settings → 200 {@link PlatformSettingsView}
 *   <li>PUT /v1/admin/settings { PlatformSettings } → 200 {@link PlatformSettingsView} (422 on bad input)
 * </ul>
 *
 * <p><strong>RBAC (admin ADD §12).</strong> Both endpoints require {@code super-admin} — a moderator
 * (or any other role) gets 403. The fee change (audited, forward-only) and the actor are handled in
 * {@link SaveSettings}; the actor is {@code jwt.getSubject()} only.
 */
@Path("/v1/admin/settings")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("super-admin")
@Tag(name = "admin-settings")
@SecurityRequirement(name = "BearerAuth")
public class AdminSettingsResource {

  private final GetSettings getSettings;
  private final SaveSettings saveSettings;
  private final JsonWebToken jwt;

  @Inject
  public AdminSettingsResource(GetSettings getSettings, SaveSettings saveSettings, JsonWebToken jwt) {
    this.getSettings = getSettings;
    this.saveSettings = saveSettings;
    this.jwt = jwt;
  }

  /** GET /v1/admin/settings — LLFR-ADMIN-10.1 (super-admin only). */
  @GET
  @Operation(summary = "Get platform settings & feature flags")
  @APIResponse(responseCode = "200", description = "Platform settings")
  @APIResponse(responseCode = "403", description = "Requires super-admin")
  public PlatformSettingsView get() {
    return getSettings.get();
  }

  /** PUT /v1/admin/settings — LLFR-ADMIN-10.1 (super-admin only; fee change audited, forward-only). */
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Update platform settings & feature flags")
  @APIResponse(responseCode = "200", description = "Updated platform settings")
  @APIResponse(responseCode = "403", description = "Requires super-admin")
  @APIResponse(responseCode = "422", description = "Invalid settings (fee out of range, bad currency, …)")
  public PlatformSettingsView put(@Valid SettingsRequest request) {
    return saveSettings.save(jwt.getSubject(), request.toInput());
  }

  /** Request body for {@code PUT /admin/settings} — matches the frontend {@code PlatformSettings} shape. */
  public record SettingsRequest(
      @Min(0) @Max(100) int platformFeePct,
      @NotBlank String payoutDay,
      @NotNull @PositiveOrZero @DecimalMax("1000000") BigDecimal payoutMinimum,
      @NotBlank String defaultCurrency,
      boolean maintenanceMode,
      @NotNull PlatformSettingsView.Providers providers,
      @NotNull PlatformSettingsView.Flags flags) {

    PlatformSettingsInput toInput() {
      return new PlatformSettingsInput(
          platformFeePct, payoutDay, payoutMinimum, defaultCurrency, maintenanceMode, providers, flags);
    }
  }
}
