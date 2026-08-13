package org.shakvilla.beatzmedia.playback.adapter.in.rest;

import java.util.Optional;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.playback.application.port.in.DownloadUrlResult;
import org.shakvilla.beatzmedia.playback.application.port.in.GetDownloadUrl;
import org.shakvilla.beatzmedia.playback.application.port.in.GetStreamUrl;
import org.shakvilla.beatzmedia.playback.application.port.in.RecordPlay;
import org.shakvilla.beatzmedia.playback.application.port.in.StreamUrlResult;
import org.shakvilla.beatzmedia.playback.domain.PlaySource;

import io.quarkus.security.Authenticated;

/**
 * Thin REST resource for the playback endpoints (LLFR-PLAYBACK-01.1 / 01.2). Maps HTTP to input
 * ports; no business logic — the INV-3 rendition decision lives entirely in
 * {@code GetStreamUrlService}. Playback ADD §5.1 / API-CONTRACT.md §4.
 *
 * <ul>
 *   <li>GET  /v1/tracks/:id/stream   → StreamUrlResponse (200); 404 TRACK_NOT_FOUND
 *   <li>POST /v1/tracks/:id/play     → 204; 404 TRACK_NOT_FOUND; 429 RATE_LIMITED (+Retry-After)
 *   <li>GET  /v1/tracks/:id/download → DownloadUrlResponse (200); 401; 404 TRACK_NOT_FOUND;
 *       403 NOT_OWNED; 409 DOWNLOAD_NOT_ALLOWED; 409 DOWNLOAD_NOT_READY
 * </ul>
 *
 * <p>Auth is optional on the two streaming endpoints (PRD §6.3 / ADD §5.1): an anonymous caller
 * gets the preview for a for-sale track and full audio for a free one; an authenticated caller's
 * identity is always derived from the verified JWT subject — never from a client-supplied
 * body/query field. The download endpoint is {@code @Authenticated}: there is no anonymous case.
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class PlaybackResource {

  private final GetStreamUrl getStreamUrl;
  private final GetDownloadUrl getDownloadUrl;
  private final RecordPlay recordPlay;
  private final PlayRateLimiter rateLimiter;
  private final JsonWebToken jwt;

  @Inject
  public PlaybackResource(
      GetStreamUrl getStreamUrl,
      GetDownloadUrl getDownloadUrl,
      RecordPlay recordPlay,
      PlayRateLimiter rateLimiter,
      JsonWebToken jwt) {
    this.getStreamUrl = getStreamUrl;
    this.getDownloadUrl = getDownloadUrl;
    this.recordPlay = recordPlay;
    this.rateLimiter = rateLimiter;
    this.jwt = jwt;
  }

  /** GET /v1/tracks/:id/stream — LLFR-PLAYBACK-01.1. */
  @GET
  @Path("/tracks/{id}/stream")
  public StreamUrlResponse getStreamUrl(@PathParam("id") String id) {
    StreamUrlResult result = getStreamUrl.getStreamUrl(new TrackId(id), callerId());
    return new StreamUrlResponse(
        result.audioUrl(), result.previewSeconds().orElse(null), result.expiresAt());
  }

  /**
   * GET /v1/tracks/:id/download — a signed URL to the lossless file, for owners whose grant
   * permits it. {@code @Authenticated} supplies the 401; every other refusal comes from the
   * service's guards, which are ordered so a stranger's answer never varies with the release's
   * download setting.
   */
  @GET
  @Path("/tracks/{id}/download")
  @Authenticated
  public DownloadUrlResponse getDownloadUrl(@PathParam("id") String id) {
    // Under @Authenticated the JWT subject is guaranteed present (same as CartResource).
    DownloadUrlResult result =
        getDownloadUrl.getDownloadUrl(new TrackId(id), new AccountId(jwt.getSubject()));
    return new DownloadUrlResponse(result.downloadUrl(), result.expiresAt(), result.format());
  }

  /** POST /v1/tracks/:id/play — LLFR-PLAYBACK-01.2. */
  @POST
  @Path("/tracks/{id}/play")
  public Response recordPlay(@PathParam("id") String id, RecordPlayRequest request) {
    Optional<AccountId> caller = callerId();
    rateLimiter.check(caller.map(AccountId::value).orElse(id));

    PlaySource source = parseSource(request != null ? request.source() : null);
    recordPlay.recordPlay(new TrackId(id), caller, source);
    return Response.noContent().build();
  }

  private PlaySource parseSource(String raw) {
    if (raw == null || raw.isBlank()) {
      return PlaySource.player;
    }
    try {
      return PlaySource.valueOf(raw);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Invalid play source: " + raw, "source");
    }
  }

  /** Extract caller account id from JWT sub, if a valid token is present. */
  private Optional<AccountId> callerId() {
    try {
      String sub = jwt.getSubject();
      return (sub != null && !sub.isBlank()) ? Optional.of(new AccountId(sub)) : Optional.empty();
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
