package org.shakvilla.beatzmedia.discovery.adapter.in.rest;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.discovery.application.port.in.ManageRecommendationInteractions;

import io.quarkus.security.Authenticated;

@Path("/v1/me")
@Authenticated
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class RecommendationInteractionResource {

  private final ManageRecommendationInteractions interactions;
  private final JsonWebToken jwt;

  @Inject
  public RecommendationInteractionResource(
      ManageRecommendationInteractions interactions, JsonWebToken jwt) {
    this.interactions = interactions;
    this.jwt = jwt;
  }

  @POST
  @Path("/recommendation-events")
  public Response events(EventsRequest request) {
    interactions.recordEvents(jwt.getSubject(), request == null ? null : request.events());
    return Response.noContent().build();
  }

  @POST
  @Path("/recommendation-feedback")
  public ManageRecommendationInteractions.FeedbackResult feedback(
      ManageRecommendationInteractions.FeedbackInput request) {
    return interactions.feedback(jwt.getSubject(), request);
  }

  @DELETE
  @Path("/recommendation-suppressions/{suppressionId}")
  public Response undo(@PathParam("suppressionId") String suppressionId) {
    interactions.undo(jwt.getSubject(), suppressionId);
    return Response.noContent().build();
  }

  public record EventsRequest(List<ManageRecommendationInteractions.EventInput> events) {}
}
