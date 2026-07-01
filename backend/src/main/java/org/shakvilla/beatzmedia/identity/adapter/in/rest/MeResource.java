package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import java.util.Optional;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.identity.application.port.in.AccountView;
import org.shakvilla.beatzmedia.identity.application.port.in.FanSettingsView;
import org.shakvilla.beatzmedia.identity.application.port.in.GetCurrentAccount;
import org.shakvilla.beatzmedia.identity.application.port.in.RequestPasswordReset;
import org.shakvilla.beatzmedia.identity.application.port.in.UpdateFanSettings;
import org.shakvilla.beatzmedia.identity.application.port.in.UpgradeToArtist;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

import io.quarkus.security.Authenticated;

/**
 * Thin REST resource for the /v1/me self-service endpoints. Maps DTOs to commands, calls input
 * ports, maps results to DTOs. No business logic here. Identity ADD §5.1.
 *
 * <ul>
 *   <li>GET /v1/me → 200 AccountDto (LLFR-IDENTITY-02.1)
 *   <li>POST /v1/me/become-artist → 200 AccountView (LLFR-IDENTITY-02.2)
 *   <li>PATCH /v1/me/settings → 200 FanSettingsDto (LLFR-IDENTITY-02.3)
 *   <li>POST /v1/me/password/reset → 204, always, public (LLFR-IDENTITY-01.5)
 * </ul>
 *
 * <p>Ownership of /me/* mutations is enforced by extracting the {@code sub} claim from the Bearer
 * JWT; the application layer re-uses the same {@code AccountId} and can never access another
 * user's data (DoD §5). {@code /me/password/reset} is the sole public exception and overrides the
 * class-level {@code @Authenticated} with {@code @PermitAll} (API-CONTRACT §2 / ADD §9).
 */
@Path("/v1/me")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class MeResource {

  private final GetCurrentAccount getCurrentAccount;
  private final UpgradeToArtist upgradeToArtist;
  private final UpdateFanSettings updateFanSettings;
  private final RequestPasswordReset requestPasswordReset;
  private final JsonWebToken jwt;

  @Inject
  public MeResource(
      GetCurrentAccount getCurrentAccount,
      UpgradeToArtist upgradeToArtist,
      UpdateFanSettings updateFanSettings,
      RequestPasswordReset requestPasswordReset,
      JsonWebToken jwt) {
    this.getCurrentAccount = getCurrentAccount;
    this.upgradeToArtist = upgradeToArtist;
    this.updateFanSettings = updateFanSettings;
    this.requestPasswordReset = requestPasswordReset;
    this.jwt = jwt;
  }

  /**
   * GET /v1/me — LLFR-IDENTITY-02.1. Returns the authenticated caller's account. A missing/expired
   * token never reaches this method — the JWT filter behind {@code @Authenticated} answers 401
   * first.
   */
  @GET
  public Response current() {
    AccountId accountId = new AccountId(jwt.getSubject());
    AccountView view = getCurrentAccount.current(accountId);
    return Response.ok(view).build();
  }

  /**
   * POST /v1/me/become-artist — LLFR-IDENTITY-02.2. Upgrades the authenticated fan to artist.
   * Idempotent: if already an artist, returns 200 with the current state. Gated by
   * ARTIST_SIGNUPS feature flag; off → 403 FEATURE_DISABLED.
   */
  @POST
  @Path("/become-artist")
  public Response becomeArtist() {
    AccountId accountId = new AccountId(jwt.getSubject());
    AccountView view = upgradeToArtist.upgrade(accountId);
    return Response.ok(view).build();
  }

  /**
   * PATCH /v1/me/settings — LLFR-IDENTITY-02.3. Partially updates fan settings for the
   * authenticated account. All patch fields are optional. Settings row created lazily with defaults
   * if it does not yet exist. Returns the full merged FanSettings.
   */
  @PATCH
  @Path("/settings")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateSettings(@Valid FanSettingsPatch patch) {
    AccountId accountId = new AccountId(jwt.getSubject());

    UpdateFanSettings.NotificationPrefs notifPrefs = null;
    if (patch.notifications() != null) {
      notifPrefs = new UpdateFanSettings.NotificationPrefs(
          patch.notifications().newReleases(),
          patch.notifications().playlistUpdates(),
          patch.notifications().dropsOffers());
    }

    UpdateFanSettings.UpdateFanSettingsCommand command = new UpdateFanSettings.UpdateFanSettingsCommand(
        Optional.ofNullable(patch.theme()),
        Optional.ofNullable(patch.audioQuality()),
        Optional.ofNullable(patch.streamingQuality()),
        Optional.ofNullable(patch.downloadQuality()),
        Optional.ofNullable(patch.crossfade()),
        Optional.ofNullable(patch.dataSaver()),
        Optional.ofNullable(notifPrefs),
        Optional.ofNullable(patch.country()),
        Optional.ofNullable(patch.phone()));

    FanSettingsView view = updateFanSettings.update(accountId, command);
    return Response.ok(FanSettingsDto.from(view)).build();
  }

  /**
   * POST /v1/me/password/reset — LLFR-IDENTITY-01.5. Public — overrides the class-level
   * {@code @Authenticated}. Always returns 204, whether or not the email is registered, to avoid
   * user enumeration (DoD §12.2). If the email exists, a single-use, time-boxed reset token is
   * generated and mailed via the {@code Mailer} port.
   */
  @POST
  @Path("/password/reset")
  @PermitAll
  @Consumes(MediaType.APPLICATION_JSON)
  public Response requestPasswordReset(@Valid PasswordResetRequest request) {
    requestPasswordReset.request(new RequestPasswordReset.RequestPasswordResetCommand(request.email()));
    return Response.noContent().build();
  }
}
