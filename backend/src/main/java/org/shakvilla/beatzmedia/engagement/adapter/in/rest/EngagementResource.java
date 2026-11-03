package org.shakvilla.beatzmedia.engagement.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.engagement.application.port.in.EngagementHistory;
import org.shakvilla.beatzmedia.engagement.application.port.in.EngagementHistory.CheckpointInput;
import org.shakvilla.beatzmedia.engagement.domain.ContentKind;
import org.shakvilla.beatzmedia.engagement.domain.EngagementEventType;

import io.quarkus.security.Authenticated;

@Path("/v1/me")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class EngagementResource {

  private final EngagementHistory engagement;
  private final JsonWebToken jwt;

  @Inject
  public EngagementResource(EngagementHistory engagement, JsonWebToken jwt) {
    this.engagement = engagement;
    this.jwt = jwt;
  }

  @POST
  @Path("/listening-sessions")
  public Response startSession() {
    var result = engagement.startSession(accountId());
    return Response.status(result.recording() ? Response.Status.CREATED : Response.Status.OK)
        .entity(result)
        .build();
  }

  @PUT
  @Path("/listening-sessions/{sessionId}/checkpoints/{sequence}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response checkpoint(
      @PathParam("sessionId") String sessionId,
      @PathParam("sequence") long sequence,
      CheckpointRequest request) {
    engagement.checkpoint(
        accountId(),
        sessionId,
        sequence,
        new CheckpointInput(
            ContentKind.fromWire(request == null ? null : request.contentKind()),
            request == null ? null : request.contentId(),
            EngagementEventType.fromWire(request == null ? null : request.eventType()),
            request == null ? -1 : request.positionSeconds(),
            request == null ? -1 : request.durationSeconds(),
            request == null ? -1 : request.listenedDeltaSeconds()));
    return Response.noContent().build();
  }

  @GET
  @Path("/listening/recent")
  public EngagementHistory.HistoryPage recent(
      @QueryParam("cursor") String cursor, @QueryParam("limit") int limit) {
    return engagement.recentlyPlayed(accountId(), cursor, limit);
  }

  @GET
  @Path("/listening/continue")
  public EngagementHistory.HistoryPage continueListening(
      @QueryParam("cursor") String cursor, @QueryParam("limit") int limit) {
    return engagement.continueListening(accountId(), cursor, limit);
  }

  @GET
  @Path("/listening/settings")
  public EngagementHistory.Preference settings() {
    return engagement.preference(accountId());
  }

  @PUT
  @Path("/listening/settings")
  @Consumes(MediaType.APPLICATION_JSON)
  public EngagementHistory.Preference updateSettings(@Valid ListeningSettingsRequest request) {
    return engagement.updatePreference(accountId(), request.historyEnabled());
  }

  @DELETE
  @Path("/listening/history")
  public Response clearHistory() {
    engagement.clearHistory(accountId());
    return Response.noContent().build();
  }

  private String accountId() {
    return jwt.getSubject();
  }
}
