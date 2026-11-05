package org.shakvilla.beatzmedia.discovery.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.discovery.application.port.in.GetPersonalizedRecommendations;

import io.quarkus.security.Authenticated;

@Path("/v1/me/recommendations")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class PersonalizedRecommendationsResource {

  private final GetPersonalizedRecommendations recommendations;
  private final JsonWebToken jwt;

  @Inject
  public PersonalizedRecommendationsResource(
      GetPersonalizedRecommendations recommendations, JsonWebToken jwt) {
    this.recommendations = recommendations;
    this.jwt = jwt;
  }

  @GET
  public GetPersonalizedRecommendations.RecommendationPage get(
      @QueryParam("limit") @DefaultValue("12") int limit) {
    return recommendations.get(jwt.getSubject(), limit);
  }
}
