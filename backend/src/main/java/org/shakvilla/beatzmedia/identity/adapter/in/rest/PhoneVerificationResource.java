package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.shakvilla.beatzmedia.identity.application.port.in.ConfirmPhoneVerification;
import org.shakvilla.beatzmedia.identity.application.port.in.GetFanSettings;
import org.shakvilla.beatzmedia.identity.application.port.in.StartPhoneVerification;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

import io.quarkus.security.Authenticated;

/** Authenticated self-service endpoints for reading settings and verifying the caller's phone. */
@Path("/v1/me")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "BearerAuth")
public class PhoneVerificationResource {
  private final GetFanSettings getFanSettings;
  private final StartPhoneVerification startPhoneVerification;
  private final ConfirmPhoneVerification confirmPhoneVerification;
  private final JsonWebToken jwt;

  @Inject
  public PhoneVerificationResource(
      GetFanSettings getFanSettings,
      StartPhoneVerification startPhoneVerification,
      ConfirmPhoneVerification confirmPhoneVerification,
      JsonWebToken jwt) {
    this.getFanSettings = getFanSettings;
    this.startPhoneVerification = startPhoneVerification;
    this.confirmPhoneVerification = confirmPhoneVerification;
    this.jwt = jwt;
  }

  @GET
  @Path("/settings")
  @Operation(summary = "Get the caller's persisted fan settings")
  @APIResponse(responseCode = "200", description = "Fan settings, including phone verification state")
  public FanSettingsDto settings() {
    return FanSettingsDto.from(getFanSettings.get(caller()));
  }

  @POST
  @Path("/phone-verification")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Send a phone verification code by SMS")
  @APIResponse(responseCode = "202", description = "Verification SMS accepted by the selected SMS provider")
  @APIResponse(responseCode = "429", description = "Verification request throttled")
  public Response start(@Valid StartPhoneVerificationRequest request) {
    return Response.accepted(startPhoneVerification.start(caller(), request.phone())).build();
  }

  @POST
  @Path("/phone-verification/confirm")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Confirm the phone verification code")
  @APIResponse(responseCode = "200", description = "Phone number verified")
  @APIResponse(responseCode = "422", description = "Code invalid, expired, or exhausted")
  public FanSettingsDto confirm(@Valid ConfirmPhoneVerificationRequest request) {
    return FanSettingsDto.from(confirmPhoneVerification.confirm(caller(), request.code()));
  }

  private AccountId caller() {
    return new AccountId(jwt.getSubject());
  }

  public record StartPhoneVerificationRequest(
      @NotBlank
      @Pattern(regexp = "^\\+?[0-9 .()\\-]{7,25}$", message = "must be a valid phone number")
      String phone) {}

  public record ConfirmPhoneVerificationRequest(
      @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "must be a 6-digit code") String code) {}
}
