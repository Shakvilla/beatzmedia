package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.identity.application.port.in.AuthResult;
import org.shakvilla.beatzmedia.identity.application.port.in.Login;
import org.shakvilla.beatzmedia.identity.application.port.in.Logout;
import org.shakvilla.beatzmedia.identity.application.port.in.RegisterFan;
import org.shakvilla.beatzmedia.identity.application.port.in.ResetPassword;
import org.shakvilla.beatzmedia.identity.application.port.in.SocialLogin;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.SocialProvider;

import io.quarkus.security.Authenticated;

/**
 * Thin REST resource for the auth endpoints. Maps DTOs to commands, calls input ports, maps
 * results to DTOs. No business logic here. Identity ADD §5.1.
 *
 * <ul>
 *   <li>POST /v1/auth/signup → 201 AuthResponse (LLFR-IDENTITY-01.1)
 *   <li>POST /v1/auth/login → 200 AuthResponse (LLFR-IDENTITY-01.2)
 *   <li>POST /v1/auth/social → 200 AuthResponse (LLFR-IDENTITY-01.3)
 *   <li>POST /v1/auth/logout → 204 (LLFR-IDENTITY-01.4)
 * </ul>
 */
@Path("/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

  private final RegisterFan registerFanPort;
  private final Login loginPort;
  private final SocialLogin socialLoginPort;
  private final Logout logoutPort;
  private final ResetPassword resetPasswordPort;
  private final JsonWebToken jwt;

  @Inject
  public AuthResource(
      RegisterFan registerFanPort,
      Login loginPort,
      SocialLogin socialLoginPort,
      Logout logoutPort,
      ResetPassword resetPasswordPort,
      JsonWebToken jwt) {
    this.registerFanPort = registerFanPort;
    this.loginPort = loginPort;
    this.socialLoginPort = socialLoginPort;
    this.logoutPort = logoutPort;
    this.resetPasswordPort = resetPasswordPort;
    this.jwt = jwt;
  }

  /**
   * POST /v1/auth/password/reset — redeem a reset token and set a new password. Public: the token
   * from the emailed link is the caller's only credential, so requiring auth here would make the
   * endpoint useless to the very users who need it.
   *
   * <p>Lives on {@code /auth} rather than {@code /me} because the caller is by definition not
   * signed in. Its sibling, the *request* endpoint, remains at {@code /me/password/reset} where the
   * contract already places it.
   *
   * <p>410 for an unknown, spent or expired token (indistinguishable by design); 422
   * {@code WEAK_PASSWORD}; 403 {@code ACCOUNT_SUSPENDED}.
   */
  @POST
  @Path("/password/reset")
  @PermitAll
  @Consumes(MediaType.APPLICATION_JSON)
  public Response resetPassword(@Valid PasswordResetConfirmRequest request) {
    resetPasswordPort.reset(
        new ResetPassword.ResetPasswordCommand(request.token(), request.password()));
    return Response.noContent().build();
  }

  /** POST /v1/auth/signup — LLFR-IDENTITY-01.1. Returns 201 with token + account. */
  @POST
  @Path("/signup")
  @PermitAll
  @Consumes(MediaType.APPLICATION_JSON)
  public Response signup(@Valid SignupRequest request) {
    AuthResult result = registerFanPort.register(
        new RegisterFan.RegisterFanCommand(request.name(), request.email(), request.password()));
    return Response.status(Response.Status.CREATED)
        .entity(new AuthResponse(result.token(), result.account()))
        .build();
  }

  /** POST /v1/auth/login — LLFR-IDENTITY-01.2. Returns 200 with token + account. */
  @POST
  @Path("/login")
  @PermitAll
  @Consumes(MediaType.APPLICATION_JSON)
  public Response login(@Valid LoginRequest request) {
    AuthResult result = loginPort.login(
        new Login.LoginCommand(request.email(), request.password()));
    return Response.ok(new AuthResponse(result.token(), result.account())).build();
  }

  /**
   * POST /v1/auth/social — LLFR-IDENTITY-01.3. Verifies the provider token, links to an existing
   * account by verified email or creates a new fan account, and returns 200 with token + account.
   * Invalid/unrecognised provider token → 401 SOCIAL_TOKEN_INVALID (thrown by the application
   * layer / {@code SocialProvider.fromWireValue}, mapped by {@code DomainExceptionMapper}).
   */
  @POST
  @Path("/social")
  @PermitAll
  @Consumes(MediaType.APPLICATION_JSON)
  public Response social(@Valid SocialRequest request) {
    SocialProvider provider = SocialProvider.fromWireValue(request.provider());
    AuthResult result = socialLoginPort.socialLogin(
        new SocialLogin.SocialLoginCommand(provider, request.token()));
    return Response.ok(new AuthResponse(result.token(), result.account())).build();
  }

  /**
   * POST /v1/auth/logout — LLFR-IDENTITY-01.4. Returns 204; idempotent. Stateless JWT — always
   * succeeds for any authenticated caller. No request body; accepts any content-type.
   */
  @POST
  @Path("/logout")
  @Authenticated
  @Consumes(MediaType.WILDCARD)
  public Response logout() {
    String subject = jwt.getSubject();
    if (subject != null && !subject.isBlank()) {
      logoutPort.logout(new AccountId(subject));
    }
    return Response.noContent().build();
  }
}
