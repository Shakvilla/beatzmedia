package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.shakvilla.beatzmedia.admin.application.port.in.ReplayPublicationEvent;
import org.shakvilla.beatzmedia.platform.application.port.in.InspectDurableEvents;
import org.shakvilla.beatzmedia.platform.domain.DurableEventFilter;
import org.shakvilla.beatzmedia.platform.domain.DurableEventRecord;
import org.shakvilla.beatzmedia.platform.domain.DurableEventStatus;
import org.shakvilla.beatzmedia.platform.domain.DurableQueueHealth;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

@Path("/v1/admin/publication-events")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"super-admin", "support"})
@Tag(name = "admin-publication-events")
@SecurityRequirement(name = "BearerAuth")
public class AdminPublicationEventResource {

  private final InspectDurableEvents inspect;
  private final ReplayPublicationEvent replay;
  private final JsonWebToken jwt;

  @Inject
  public AdminPublicationEventResource(
      InspectDurableEvents inspect, ReplayPublicationEvent replay, JsonWebToken jwt) {
    this.inspect = inspect;
    this.replay = replay;
    this.jwt = jwt;
  }

  @GET
  @Operation(summary = "Search durable publication events")
  @APIResponse(responseCode = "200", description = "Filtered publication event metadata")
  public Page<DurableEventDto> search(
      @QueryParam("status") String status,
      @QueryParam("eventType") String eventType,
      @QueryParam("aggregateId") String aggregateId,
      @QueryParam("occurredFrom") String occurredFrom,
      @QueryParam("occurredTo") String occurredTo,
      @DefaultValue("1") @QueryParam("page") int page,
      @DefaultValue("20") @QueryParam("size") int size) {
    Page<DurableEventRecord> result = inspect.search(new DurableEventFilter(
        DurableEventStatus.fromWireValue(status),
        eventType,
        aggregateId,
        parseInstant(occurredFrom, "occurredFrom"),
        parseInstant(occurredTo, "occurredTo"),
        new PageRequest(page, size)));
    return Page.of(
        result.items().stream().map(DurableEventDto::from).toList(),
        result.page(),
        result.size(),
        result.total());
  }

  @GET
  @Path("/health")
  @Operation(summary = "Get durable publication queue health")
  @APIResponse(responseCode = "200", description = "Publication queue health")
  public DurableQueueHealth health() {
    return inspect.health();
  }

  @POST
  @Path("/{eventId}/replay")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("super-admin")
  @Operation(summary = "Replay a failed durable publication event")
  @APIResponse(responseCode = "200", description = "Event returned to the pending queue")
  @APIResponse(responseCode = "409", description = "Event is not failed or has no compatible handler")
  public DurableEventDto replay(
      @PathParam("eventId") String eventId, @Valid @NotNull ReplayRequest request) {
    return DurableEventDto.from(replay.replay(jwt.getSubject(), eventId, request.reason()));
  }

  public record ReplayRequest(@NotBlank String reason) {}

  public record DurableEventDto(
      String id,
      String eventType,
      int schemaVersion,
      String aggregateType,
      String aggregateId,
      Instant occurredAt,
      Instant availableAt,
      String status,
      int attemptCount,
      int replayCount,
      Instant claimedUntil,
      Instant completedAt,
      String lastError,
      Instant lastReplayedAt,
      String lastReplayedBy) {

    static DurableEventDto from(DurableEventRecord event) {
      return new DurableEventDto(
          event.id(),
          event.eventType(),
          event.schemaVersion(),
          event.aggregateType(),
          event.aggregateId(),
          event.occurredAt(),
          event.availableAt(),
          event.status().wireValue(),
          event.attemptCount(),
          event.replayCount(),
          event.claimedUntil(),
          event.completedAt(),
          event.lastError(),
          event.lastReplayedAt(),
          event.lastReplayedBy());
    }
  }

  private static Instant parseInstant(String value, String field) {
    if (value == null || value.isBlank()) return null;
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException invalid) {
      throw new ValidationException(field + " must be an ISO-8601 instant", field);
    }
  }
}
