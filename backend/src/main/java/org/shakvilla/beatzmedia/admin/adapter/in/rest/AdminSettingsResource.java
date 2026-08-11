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
      @Valid @NotNull ProvidersRequest providers,
      @Valid @NotNull FlagsRequest flags) {

    PlatformSettingsInput toInput() {
      return new PlatformSettingsInput(
          platformFeePct,
          payoutDay,
          payoutMinimum,
          defaultCurrency,
          maintenanceMode,
          providers.toProviders(),
          flags.toFlags());
    }
  }

  /**
   * The feature-flag half of the request body, separate from the response's
   * {@link PlatformSettingsView.Flags} so every key can be <strong>required</strong>.
   *
   * <p><strong>Why this exists (GAP-09).</strong> The request reused the response record, whose
   * fields are primitive {@code boolean}. Quarkus disables Jackson's fail-on-unknown-properties, so
   * a key the server did not recognise was dropped in silence and the corresponding field fell back
   * to its default — {@code false}. {@code SaveSettingsService} then wrote all five flags
   * unconditionally. So {@code PUT /v1/admin/settings} with {@code flags: { PODCASTS: false }} (the
   * enum spelling instead of the wire's {@code podcasts}) returned {@code 200 OK} having
   * <strong>disabled every feature on the platform</strong> — podcasts, events, tipping, artist
   * signups and fan messaging — while reporting success.
   *
   * <p>The gap report recorded this as "returns 200 and changes nothing". That was too generous:
   * the write happens, it just writes {@code false} everywhere. A partial body — sending only the
   * one flag being toggled, which is the natural thing for a client to do — had the same effect.
   *
   * <p>Boxed {@code Boolean} plus {@code @NotNull} makes an absent key indistinguishable from a
   * misspelled one, and both a {@code 422} naming the offending field. Requiring every key rather
   * than defaulting the missing ones is deliberate: a flag is a kill switch, and quietly inferring
   * "off" for one the caller never mentioned is exactly the failure being fixed.
   */
  /**
   * The payment-rail half of the request body, separate from the response's
   * {@link PlatformSettingsView.Providers} so every key can be <strong>required</strong>.
   *
   * <p><strong>Why (GAP-13, and GAP-09 all over again).</strong> The request reused the response
   * record, whose fields are primitive {@code boolean}. Quarkus disables Jackson's
   * fail-on-unknown-properties, so a key the server does not recognise is dropped in silence and the
   * field falls back to {@code false} — and {@code SaveSettingsService} writes all five rails
   * unconditionally.
   *
   * <p>Renaming {@code momo}/{@code vodafone} to {@code mtn}/{@code telecel} made every existing
   * caller a stale caller overnight. A client still sending the old names would have had its
   * settings save accepted with {@code 200 OK} while <strong>switching MTN and Telecel off
   * platform-wide</strong> — payments stopping on the two largest rails in Ghana, reported as
   * success. This was not theoretical: three integration tests did exactly that, which is how it was
   * found.
   *
   * <p>Boxed {@code Boolean} plus {@code @NotNull} turns a misspelled or omitted rail into a
   * {@code 422} naming the field, so a stale client fails loudly instead of disabling payments.
   */
  public record ProvidersRequest(
      @NotNull Boolean mtn,
      @NotNull Boolean telecel,
      @NotNull Boolean airteltigo,
      @NotNull Boolean card,
      @NotNull Boolean bank) {

    PlatformSettingsView.Providers toProviders() {
      return new PlatformSettingsView.Providers(mtn, telecel, airteltigo, card, bank);
    }
  }

  public record FlagsRequest(
      @NotNull Boolean artistSignups,
      @NotNull Boolean podcasts,
      @NotNull Boolean events,
      @NotNull Boolean tipping,
      @NotNull Boolean fanMessaging) {

    PlatformSettingsView.Flags toFlags() {
      return new PlatformSettingsView.Flags(
          artistSignups, podcasts, events, tipping, fanMessaging);
    }
  }
}
