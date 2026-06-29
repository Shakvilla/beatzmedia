package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.identity.application.port.in.AccountView;
import org.shakvilla.beatzmedia.identity.application.port.in.FanSettingsView;
import org.shakvilla.beatzmedia.identity.application.port.in.UpdateFanSettings;
import org.shakvilla.beatzmedia.identity.application.port.in.UpgradeToArtist;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

import io.quarkus.security.Authenticated;

/**
 * Thin REST resource for the /v1/me self-service endpoints introduced in WU-IDN-3. Maps DTOs to
 * commands, calls input ports, maps results to DTOs. No business logic here. Identity ADD §5.1.
 *
 * <ul>
 *   <li>POST /v1/me/become-artist → 200 AccountView (LLFR-IDENTITY-02.2)
 *   <li>PATCH /v1/me/settings → 200 FanSettingsDto (LLFR-IDENTITY-02.3)
 * </ul>
 *
 * <p>Ownership of /me/settings is enforced by extracting the {@code sub} claim from the Bearer JWT;
 * the application layer re-uses the same {@code AccountId} and can never access another user's data
 * (DoD §5).
 */
@Path("/v1/me")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class MeResource {

  private final UpgradeToArtist upgradeToArtist;
  private final UpdateFanSettings updateFanSettings;
  private final JsonWebToken jwt;

  @Inject
  public MeResource(
      UpgradeToArtist upgradeToArtist,
      UpdateFanSettings updateFanSettings,
      JsonWebToken jwt) {
    this.upgradeToArtist = upgradeToArtist;
    this.updateFanSettings = updateFanSettings;
    this.jwt = jwt;
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
}
