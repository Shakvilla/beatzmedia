package org.shakvilla.beatzmedia.discovery.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.discovery.application.port.in.GetFollowingFeed;

import io.quarkus.security.Authenticated;

@Path("/v1/me/following")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class FollowingFeedResource {

  private final GetFollowingFeed following;
  private final JsonWebToken jwt;

  @Inject
  public FollowingFeedResource(GetFollowingFeed following, JsonWebToken jwt) {
    this.following = following;
    this.jwt = jwt;
  }

  @GET
  public GetFollowingFeed.FollowingPage get(
      @QueryParam("cursor") String cursor,
      @QueryParam("limit") @DefaultValue("20") int limit) {
    return following.get(jwt.getSubject(), cursor, limit);
  }
}
