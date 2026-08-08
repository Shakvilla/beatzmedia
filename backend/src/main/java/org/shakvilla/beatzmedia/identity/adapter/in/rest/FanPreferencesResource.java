package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.identity.application.port.in.ManageFanPreferences;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.FanPreferences;

import io.quarkus.security.Authenticated;

/**
 * The signed-in fan's taste profile and onboarding state.
 *
 * <p>{@code GET} never 404s: a fan who has never onboarded reads an empty profile with
 * {@code onboarded: false}, which is what the client's onboarding gate keys off.
 */
@Path("/v1/me/preferences")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class FanPreferencesResource {

  private final ManageFanPreferences preferences;
  private final JsonWebToken jwt;

  @Inject
  public FanPreferencesResource(ManageFanPreferences preferences, JsonWebToken jwt) {
    this.preferences = preferences;
    this.jwt = jwt;
  }

  @GET
  public FanPreferencesDto get() {
    return FanPreferencesDto.from(preferences.get(actor()));
  }

  /**
   * Finishes onboarding. 422 when fewer than three genres are sent, or when any genre is not an
   * active term — a stale client must not be able to record a genre that no longer exists.
   */
  @POST
  @Path("/onboarding")
  @Consumes(MediaType.APPLICATION_JSON)
  public FanPreferencesDto completeOnboarding(@Valid GenresRequest request) {
    return FanPreferencesDto.from(preferences.completeOnboarding(actor(), request.genres()));
  }

  /** Edits the taste profile later, without re-running the gate. */
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public FanPreferencesDto updateGenres(@Valid GenresRequest request) {
    return FanPreferencesDto.from(preferences.updateGenres(actor(), request.genres()));
  }

  private AccountId actor() {
    return new AccountId(jwt.getSubject());
  }

  public record GenresRequest(@NotNull(message = "genres must not be null") List<String> genres) {}

  public record FanPreferencesDto(
      List<String> preferredGenres, boolean onboarded, Instant completedAt) {

    public static FanPreferencesDto from(FanPreferences p) {
      return new FanPreferencesDto(p.preferredGenres(), p.isOnboarded(), p.completedAt());
    }
  }
}
