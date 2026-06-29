package org.shakvilla.beatzmedia.catalog.adapter.in.rest;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.shakvilla.beatzmedia.catalog.application.port.in.DeleteRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.ListStudioReleases;
import org.shakvilla.beatzmedia.catalog.application.port.in.PageView;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseView;
import org.shakvilla.beatzmedia.catalog.application.port.in.SubmitRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.SubmitRelease.SplitEntryCommand;
import org.shakvilla.beatzmedia.catalog.application.port.in.SubmitRelease.SubmitReleaseCommand;
import org.shakvilla.beatzmedia.catalog.application.port.in.SubmitRelease.UploadedTrackRef;
import org.shakvilla.beatzmedia.catalog.application.port.in.UpdateRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.UpdateRelease.UpdateReleaseCommand;
import org.shakvilla.beatzmedia.catalog.application.port.in.UploadReleaseTrack;
import org.shakvilla.beatzmedia.catalog.application.port.in.UploadReleaseTrack.AudioUpload;
import org.shakvilla.beatzmedia.catalog.application.port.in.UploadedTrackView;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;

/**
 * REST resource for the artist Studio release lifecycle endpoints (LLFR-CATALOG-02.1 – 02.4).
 * Thin: DTO in → command → port → DTO out. No business logic here.
 *
 * <ul>
 *   <li>GET /v1/studio/releases — list own releases
 *   <li>POST /v1/studio/releases — submit new release (Idempotency-Key header required)
 *   <li>GET /v1/studio/releases/:id — get one release
 *   <li>PATCH /v1/studio/releases/:id — update metadata
 *   <li>DELETE /v1/studio/releases/:id — delete draft/in_review
 *   <li>POST /v1/studio/releases/:id/tracks — multipart WAV/FLAC upload
 * </ul>
 */
@Path("/v1/studio/releases")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("artist")
public class StudioReleaseResource {

  private final ListStudioReleases listStudioReleases;
  private final SubmitRelease submitRelease;
  private final GetRelease getRelease;
  private final UpdateRelease updateRelease;
  private final DeleteRelease deleteRelease;
  private final UploadReleaseTrack uploadReleaseTrack;
  private final JsonWebToken jwt;

  @Inject
  public StudioReleaseResource(
      ListStudioReleases listStudioReleases,
      SubmitRelease submitRelease,
      GetRelease getRelease,
      UpdateRelease updateRelease,
      DeleteRelease deleteRelease,
      UploadReleaseTrack uploadReleaseTrack,
      JsonWebToken jwt) {
    this.listStudioReleases = listStudioReleases;
    this.submitRelease = submitRelease;
    this.getRelease = getRelease;
    this.updateRelease = updateRelease;
    this.deleteRelease = deleteRelease;
    this.uploadReleaseTrack = uploadReleaseTrack;
    this.jwt = jwt;
  }

  /** GET /v1/studio/releases?status=&page=&size= — LLFR-CATALOG-02.1. */
  @GET
  public PageView<StudioReleaseView> list(
      @QueryParam("status") String status,
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("size") @DefaultValue("20") int size) {
    Optional<ReleaseStatus> statusFilter = Optional.ofNullable(status)
        .filter(s -> !s.isBlank())
        .map(s -> ReleaseStatus.valueOf(s.replace('-', '_')));
    return listStudioReleases.list(artistId(), statusFilter, page, size);
  }

  /** POST /v1/studio/releases — LLFR-CATALOG-02.2. */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response submit(
      @HeaderParam("Idempotency-Key") String idempotencyKey,
      SubmitReleaseBody body) {
    SubmitReleaseCommand cmd = new SubmitReleaseCommand(
        idempotencyKey,
        artistId(),
        body.title(),
        ReleaseType.valueOf(body.type()),
        body.visibility() != null ? Visibility.fromDbValue(body.visibility()) : Visibility.PUBLIC,
        body.scheduledAt(),
        body.tracks() != null
            ? body.tracks().stream()
                .map(t -> new UploadedTrackRef(
                    t.trackId(),
                    t.position(),
                    t.priceMinor(),
                    t.splits() != null
                        ? t.splits().stream()
                            .map(s -> new SplitEntryCommand(
                                s.name(), s.email(), s.role(), s.percent(), s.confirmation()))
                            .toList()
                        : List.of()))
                .toList()
            : List.of());
    StudioReleaseView view = submitRelease.submit(cmd);
    return Response.status(Response.Status.CREATED).entity(view).build();
  }

  /** GET /v1/studio/releases/:id — LLFR-CATALOG-02.3. */
  @GET
  @Path("/{id}")
  public StudioReleaseView get(@PathParam("id") String id) {
    return getRelease.get(new ReleaseId(id), artistId());
  }

  /** PATCH /v1/studio/releases/:id — LLFR-CATALOG-02.3. */
  @PATCH
  @Path("/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  public StudioReleaseView update(
      @PathParam("id") String id, UpdateReleaseBody body) {
    return updateRelease.update(
        new ReleaseId(id), artistId(), new UpdateReleaseCommand(body.title()));
  }

  /** DELETE /v1/studio/releases/:id — LLFR-CATALOG-02.3. */
  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") String id) {
    deleteRelease.delete(new ReleaseId(id), artistId());
    return Response.noContent().build();
  }

  /**
   * POST /v1/studio/releases/:id/tracks — LLFR-CATALOG-02.4. Accepts multipart WAV/FLAC. Returns
   * 201 UploadedTrackView.
   */
  @POST
  @Path("/{id}/tracks")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadTrack(
      @PathParam("id") String releaseId,
      @MultipartForm TrackUploadForm form) {
    FileUpload file = form.file();
    String filename = file.fileName();
    String contentType = file.contentType();
    long size = file.size();

    try (InputStream body = java.nio.file.Files.newInputStream(file.uploadedFile())) {
      // Compute SHA-256 content hash for idempotency
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      InputStream digestBody = new DigestInputStream(body, digest);

      AudioUpload upload = new AudioUpload(filename, contentType, size, digestBody, null);
      UploadedTrackView view =
          uploadReleaseTrack.upload(new ReleaseId(releaseId), artistId(), upload);

      return Response.status(Response.Status.CREATED).entity(view).build();
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new RuntimeException("Failed to read uploaded file", e);
    }
  }

  private ArtistId artistId() {
    return new ArtistId(jwt.getSubject());
  }

  // ---- DTO types (inner records for JSON binding) ----

  public record SubmitReleaseBody(
      String title,
      String type,
      String visibility,
      String scheduledAt,
      List<TrackRef> tracks) {}

  public record TrackRef(
      String trackId,
      int position,
      long priceMinor,
      List<SplitRef> splits) {}

  public record SplitRef(
      String name,
      String email,
      String role,
      int percent,
      String confirmation) {}

  public record UpdateReleaseBody(String title) {}
}
