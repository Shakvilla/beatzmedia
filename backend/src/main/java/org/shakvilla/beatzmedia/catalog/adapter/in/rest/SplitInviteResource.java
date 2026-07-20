package org.shakvilla.beatzmedia.catalog.adapter.in.rest;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.catalog.application.port.in.AcceptSplitInvite;
import org.shakvilla.beatzmedia.catalog.application.port.in.DeclineSplitInvite;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetSplitInvite;
import org.shakvilla.beatzmedia.catalog.application.port.in.SplitInviteView;

import io.quarkus.security.Authenticated;

/**
 * Public collaborator split invite surface (WU-CAT-9). GET + decline are {@code @PermitAll} (a
 * collaborator may not have an account); accept requires authentication so the split can link to a
 * real account. Thin: token in → port → DTO/204 out. Domain exceptions map to 404/410 via
 * DomainExceptionMapper.
 */
@Path("/v1/splits/invites")
@Produces(MediaType.APPLICATION_JSON)
public class SplitInviteResource {

  private final GetSplitInvite getSplitInvite;
  private final AcceptSplitInvite acceptSplitInvite;
  private final DeclineSplitInvite declineSplitInvite;
  private final JsonWebToken jwt;

  @Inject
  public SplitInviteResource(GetSplitInvite getSplitInvite, AcceptSplitInvite acceptSplitInvite,
      DeclineSplitInvite declineSplitInvite, JsonWebToken jwt) {
    this.getSplitInvite = getSplitInvite;
    this.acceptSplitInvite = acceptSplitInvite;
    this.declineSplitInvite = declineSplitInvite;
    this.jwt = jwt;
  }

  @GET
  @Path("/{token}")
  @PermitAll
  public SplitInviteView get(@PathParam("token") String token) {
    return getSplitInvite.getByToken(token);
  }

  @POST
  @Path("/{token}/accept")
  @Authenticated
  public Response accept(@PathParam("token") String token) {
    acceptSplitInvite.accept(token, jwt.getSubject());
    return Response.noContent().build();
  }

  @POST
  @Path("/{token}/decline")
  @PermitAll
  public Response decline(@PathParam("token") String token) {
    declineSplitInvite.decline(token);
    return Response.noContent().build();
  }
}
