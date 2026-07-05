package org.shakvilla.beatzmedia.notifications.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.in.ListNotifications;
import org.shakvilla.beatzmedia.notifications.application.port.in.MarkAllRead;
import org.shakvilla.beatzmedia.notifications.application.port.in.MarkOneRead;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotificationFeed;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

import io.quarkus.security.Authenticated;

/**
 * Thin REST resource for the authenticated user's in-app notification feed (LLFR-NOTIF-01.1 –
 * 01.3). Maps HTTP to input ports; no business logic. Notifications ADD §5.1 / API-CONTRACT.md
 * §10.
 *
 * <ul>
 *   <li>GET /v1/me/notifications?page=&amp;size= → 200 {@code NotificationListResponse}
 *   <li>POST /v1/me/notifications/read → 204 (mark all read)
 *   <li>POST /v1/me/notifications/:id/read → 204 (mark one read); 404 if not owned/missing
 * </ul>
 *
 * <p><strong>Scope (INV-N1, no IDOR).</strong> Every operation is scoped to the verified JWT
 * {@code sub} — the caller's account id is NEVER accepted from a path/query/body parameter. A
 * caller can only ever see or mutate their own notifications.
 */
@Path("/v1/me/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class NotificationResource {

  private final ListNotifications listNotifications;
  private final MarkAllRead markAllRead;
  private final MarkOneRead markOneRead;
  private final JsonWebToken jwt;

  @Inject
  public NotificationResource(
      ListNotifications listNotifications,
      MarkAllRead markAllRead,
      MarkOneRead markOneRead,
      JsonWebToken jwt) {
    this.listNotifications = listNotifications;
    this.markAllRead = markAllRead;
    this.markOneRead = markOneRead;
    this.jwt = jwt;
  }

  /** GET /v1/me/notifications?page=&size= — LLFR-NOTIF-01.1. */
  @GET
  public Response list(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("20") int size) {
    NotificationFeed feed = listNotifications.list(callerId(), new PageRequest(page, size));
    return Response.ok(NotificationListResponse.from(feed)).build();
  }

  /** POST /v1/me/notifications/read — LLFR-NOTIF-01.2. Idempotent: re-issue is still 204. */
  @POST
  @Path("/read")
  public Response markAll() {
    markAllRead.markAllRead(callerId());
    return Response.noContent().build();
  }

  /**
   * POST /v1/me/notifications/:id/read — LLFR-NOTIF-01.3. Owner-only; a non-owner or missing id
   * both surface as 404 (existence hidden, INV-N1). Idempotent (INV-N2).
   */
  @POST
  @Path("/{id}/read")
  public Response markOne(@PathParam("id") String id) {
    markOneRead.markOneRead(callerId(), new NotificationId(id));
    return Response.noContent().build();
  }

  /** Extract the caller's account id from the verified JWT subject — never client-supplied. */
  private AccountId callerId() {
    return new AccountId(jwt.getSubject());
  }
}
