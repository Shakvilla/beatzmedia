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
import org.shakvilla.beatzmedia.catalog.application.port.in.CreateReleaseDraft;
import org.shakvilla.beatzmedia.catalog.application.port.in.CreateReleaseDraft.CreateDraftCommand;
import org.shakvilla.beatzmedia.catalog.application.port.in.DeleteRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.FinalizeRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.ListStudioReleases;
import org.shakvilla.beatzmedia.catalog.application.port.in.PageView;
import org.shakvilla.beatzmedia.catalog.application.port.in.RemoveReleaseTrack;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResendSplitInvites;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseDetailView;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseView;
import org.shakvilla.beatzmedia.catalog.application.port.in.UpdateRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.UpdateRelease.TrackRef;
import org.shakvilla.beatzmedia.catalog.application.port.in.UpdateRelease.UpdateReleaseCommand;
import org.shakvilla.beatzmedia.catalog.application.port.in.UploadReleaseTrack;
import org.shakvilla.beatzmedia.catalog.application.port.in.UploadReleaseTrack.AudioUpload;
import org.shakvilla.beatzmedia.catalog.application.port.in.UploadedTrackView;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.MissingIdempotencyKeyException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;

/**
 * REST resource for the artist Studio release create flow (LLFR-CATALOG-02.1 – 02.5): draft
 * create → upload-attached → edit → finalize. Thin: DTO in → command → port → DTO out. No
 * business logic here.
 *
 * <ul>
 *   <li>GET /v1/studio/releases — list own releases
 *   <li>POST /v1/studio/releases — create a metadata-only draft
 *   <li>GET /v1/studio/releases/:id — get one release (detail view)
 *   <li>PATCH /v1/studio/releases/:id — update draft metadata + track list
 *   <li>DELETE /v1/studio/releases/:id — delete draft/in_review
 *   <li>POST /v1/studio/releases/:id/tracks — multipart WAV/FLAC upload, attaches to the draft
 *   <li>DELETE /v1/studio/releases/:id/tracks/:trackId — remove a draft track
 *   <li>POST /v1/studio/releases/:id/submit — finalize draft -> in_review (Idempotency-Key
 *       required)
 * </ul>
 */
@Path("/v1/studio/releases")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("artist")
public class StudioReleaseResource {

  private final ListStudioReleases listStudioReleases;
  private final CreateReleaseDraft createReleaseDraft;
  private final GetRelease getRelease;
  private final UpdateRelease updateRelease;
  private final DeleteRelease deleteRelease;
  private final UploadReleaseTrack uploadReleaseTrack;
  private final RemoveReleaseTrack removeReleaseTrack;
  private final FinalizeRelease finalizeRelease;
  private final ResendSplitInvites resendSplitInvites;
  private final JsonWebToken jwt;

  @Inject
  public StudioReleaseResource(
      ListStudioReleases listStudioReleases,
      CreateReleaseDraft createReleaseDraft,
      GetRelease getRelease,
      UpdateRelease updateRelease,
      DeleteRelease deleteRelease,
      UploadReleaseTrack uploadReleaseTrack,
      RemoveReleaseTrack removeReleaseTrack,
      FinalizeRelease finalizeRelease,
      ResendSplitInvites resendSplitInvites,
      JsonWebToken jwt) {
    this.listStudioReleases = listStudioReleases;
    this.createReleaseDraft = createReleaseDraft;
    this.getRelease = getRelease;
    this.updateRelease = updateRelease;
    this.deleteRelease = deleteRelease;
    this.uploadReleaseTrack = uploadReleaseTrack;
    this.removeReleaseTrack = removeReleaseTrack;
    this.finalizeRelease = finalizeRelease;
    this.resendSplitInvites = resendSplitInvites;
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

  /**
   * POST /v1/studio/releases — LLFR-CATALOG-02.2. Creates a metadata-only draft (status
   * "draft", empty tracks). No Idempotency-Key required — drafts are cheap and deletable.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createDraft(CreateDraftBody body) {
    CreateDraftCommand cmd = new CreateDraftCommand(
        artistId(),
        body.title(),
        ReleaseType.valueOf(body.type()),
        body.visibility() != null ? Visibility.fromDbValue(body.visibility()) : Visibility.SCHEDULED,
        body.scheduledAt() != null ? java.time.Instant.parse(body.scheduledAt()) : null,
        body.genre(),
        body.description());
    StudioReleaseDetailView view = createReleaseDraft.create(cmd);
    return Response.status(Response.Status.CREATED).entity(view).build();
  }

  /** GET /v1/studio/releases/:id — LLFR-CATALOG-02.3. Returns the additive detail view. */
  @GET
  @Path("/{id}")
  public StudioReleaseDetailView get(@PathParam("id") String id) {
    return getRelease.get(new ReleaseId(id), artistId());
  }

  /**
   * PATCH /v1/studio/releases/:id — LLFR-CATALOG-02.3. {@code title} is editable on any status;
   * {@code genre}/{@code description}/{@code visibility}/{@code scheduledAt}/{@code tracks} are
   * draft-only (409 ILLEGAL_TRANSITION otherwise).
   */
  @PATCH
  @Path("/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  public StudioReleaseDetailView update(
      @PathParam("id") String id, @jakarta.validation.Valid UpdateReleaseBody body) {
    UpdateReleaseCommand cmd = new UpdateReleaseCommand(
        body.title(),
        body.genre(),
        body.description(),
        body.visibility(),
        body.scheduledAt() != null ? java.time.Instant.parse(body.scheduledAt()) : null,
        body.tracks() != null
            ? body.tracks().stream()
                .map(t -> new TrackRef(
                    t.trackId(), t.position(), t.priceMinor(),
                    t.splits() == null ? null
                        : t.splits().stream()
                            .map(s -> new UpdateRelease.SplitRef(s.name(), s.email(), s.role(), s.percent()))
                            .toList()))
                .toList()
            : null);
    return updateRelease.update(new ReleaseId(id), artistId(), cmd);
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
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      DigestInputStream digestBody = new DigestInputStream(body, digest);

      // Read the stream so the digest is computed before passing to the service
      byte[] buf = new byte[8192];
      //noinspection StatementWithEmptyBody
      while (digestBody.read(buf) != -1) {}

      String contentHash = bytesToHex(digest.digest());

      // Re-open the file for the actual upload command (the stream was consumed above)
      try (InputStream uploadBody = java.nio.file.Files.newInputStream(file.uploadedFile())) {
        AudioUpload upload = new AudioUpload(filename, contentType, size, uploadBody, contentHash);
        UploadedTrackView view =
            uploadReleaseTrack.upload(new ReleaseId(releaseId), artistId(), upload);
        return Response.status(Response.Status.CREATED).entity(view).build();
      }
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new RuntimeException("Failed to read uploaded file", e);
    }
  }

  /**
   * DELETE /v1/studio/releases/:id/tracks/:trackId — WU-CAT-5. Draft-only (409
   * ILLEGAL_TRANSITION otherwise); unknown track → 404 TRACK_NOT_FOUND.
   */
  @DELETE
  @Path("/{id}/tracks/{trackId}")
  public Response removeTrack(
      @PathParam("id") String releaseId, @PathParam("trackId") String trackId) {
    removeReleaseTrack.remove(new ReleaseId(releaseId), artistId(), new TrackId(trackId));
    return Response.noContent().build();
  }

  /**
   * POST /v1/studio/releases/:id/submit — WU-CAT-5. Finalizes a draft: {@code draft ->
   * in_review}. Requires the {@code Idempotency-Key} header (400 MISSING_IDEMPOTENCY_KEY if
   * absent/blank). Not-draft → 409 ILLEGAL_TRANSITION; INV-12 track-count mismatch → 422
   * TRACK_COUNT_INVALID.
   */
  @POST
  @Path("/{id}/submit")
  public StudioReleaseDetailView submit(
      @PathParam("id") String id, @HeaderParam("Idempotency-Key") String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new MissingIdempotencyKeyException();
    }
    return finalizeRelease.finalize(new ReleaseId(id), artistId(), idempotencyKey);
  }

  /**
   * POST /v1/studio/releases/:id/resend-invites — WU-CAT-9. Re-issues collaborator split invites
   * for every still-pending split on the release. Owner-only (403 UNAUTHORIZED otherwise); unknown
   * release → 404 RELEASE_NOT_FOUND.
   */
  @POST
  @Path("/{id}/resend-invites")
  public Response resendInvites(@PathParam("id") String id) {
    resendSplitInvites.resend(new ReleaseId(id), artistId());
    return Response.noContent().build();
  }

  private ArtistId artistId() {
    return new ArtistId(jwt.getSubject());
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  // ---- DTO types (inner records for JSON binding) ----

  public record CreateDraftBody(
      String title,
      String type,
      String visibility,
      String scheduledAt,
      String genre,
      String description) {}

  public record UpdateReleaseBody(
      String title,
      String genre,
      String description,
      String visibility,
      String scheduledAt,
      @jakarta.validation.Valid List<TrackRefBody> tracks) {}

  public record TrackRefBody(
      String trackId, int position, long priceMinor,
      @jakarta.validation.Valid List<SplitRefBody> splits) {}

  public record SplitRefBody(
      String name, String email, String role,
      @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(100) int percent) {}
}
