package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.shakvilla.beatzmedia.admin.application.port.in.ManageProviderConfig;
import org.shakvilla.beatzmedia.platform.application.port.in.ManageProviderSelections.ProviderSelectionView;

@Path("/v1/admin/provider-config")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("super-admin")
@Tag(name = "Administrator Management")
@SecurityRequirement(name = "BearerAuth")
public class AdminProviderConfigResource {
  private final ManageProviderConfig providerConfig;
  private final JsonWebToken jwt;

  @Inject
  public AdminProviderConfigResource(ManageProviderConfig providerConfig, JsonWebToken jwt) {
    this.providerConfig = providerConfig;
    this.jwt = jwt;
  }

  @GET
  @Operation(summary = "List active providers and readiness")
  @APIResponse(responseCode = "200", description = "Provider selections without credentials")
  @APIResponse(responseCode = "403", description = "Requires super-admin")
  public ProviderConfigResponse get() {
    return new ProviderConfigResponse(providerConfig.list());
  }

  @GET
  @Path("/resend")
  @Operation(summary = "Read Resend configuration status without returning credentials")
  @APIResponse(responseCode = "200", description = "Redacted Resend configuration")
  public ManageProviderConfig.ResendConfigView getResendConfig() {
    return providerConfig.resendConfig();
  }

  @PUT
  @Path("/resend")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Save encrypted Resend credentials and sender")
  @APIResponse(responseCode = "200", description = "Redacted saved configuration")
  @APIResponse(responseCode = "409", description = "Stale expectedVersion")
  @APIResponse(responseCode = "422", description = "Invalid settings or encryption key unavailable")
  public ManageProviderConfig.ResendConfigView putResendConfig(
      @Valid SaveResendConfigRequest request) {
    return providerConfig.saveResendConfig(
        jwt.getSubject(),
        request.apiKey(),
        request.from(),
        request.expectedVersion());
  }

  @GET
  @Path("/frog")
  @Operation(summary = "Read FROG configuration status without returning credentials")
  @APIResponse(responseCode = "200", description = "Redacted FROG configuration")
  public ManageProviderConfig.FrogConfigView getFrogConfig() {
    return providerConfig.frogConfig();
  }

  @PUT
  @Path("/frog")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Save encrypted FROG credentials and sender ID")
  @APIResponse(responseCode = "200", description = "Redacted saved configuration")
  @APIResponse(responseCode = "409", description = "Stale expectedVersion")
  @APIResponse(responseCode = "422", description = "Invalid settings or encryption key unavailable")
  public ManageProviderConfig.FrogConfigView putFrogConfig(@Valid SaveFrogConfigRequest request) {
    return providerConfig.saveFrogConfig(
        jwt.getSubject(), request.apiKey(), request.username(), request.senderId(), request.expectedVersion());
  }

  @PUT
  @Path("/{capability}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Change the provider for one capability")
  @APIResponse(responseCode = "200", description = "Updated provider selection")
  @APIResponse(responseCode = "409", description = "Stale expectedVersion")
  @APIResponse(responseCode = "422", description = "Invalid or unready provider")
  public ProviderSelectionView put(
      @PathParam("capability") String capability, @Valid ChangeProviderRequest request) {
    return providerConfig.change(
        jwt.getSubject(), capability, request.provider(), request.expectedVersion());
  }

  public record ProviderConfigResponse(List<ProviderSelectionView> items) {}

  public record ChangeProviderRequest(@NotBlank String provider, @Min(1) long expectedVersion) {}

  public record SaveResendConfigRequest(
      String apiKey, String from, @Min(0) long expectedVersion) {}

  public record SaveFrogConfigRequest(
      String apiKey, String username, String senderId, @Min(0) long expectedVersion) {}
}
