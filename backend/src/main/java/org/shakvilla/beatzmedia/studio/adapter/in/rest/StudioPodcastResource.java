package org.shakvilla.beatzmedia.studio.adapter.in.rest;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.studio.application.port.in.CreateEpisode;
import org.shakvilla.beatzmedia.studio.application.port.in.CreateEpisode.AudioUpload;
import org.shakvilla.beatzmedia.studio.application.port.in.CreateEpisode.CreateEpisodeCommand;
import org.shakvilla.beatzmedia.studio.application.port.in.CreatePodcastShow;
import org.shakvilla.beatzmedia.studio.application.port.in.CreatePodcastShow.CreatePodcastShowCommand;
import org.shakvilla.beatzmedia.studio.application.port.in.DeleteEpisode;
import org.shakvilla.beatzmedia.studio.application.port.in.EpisodeView;
import org.shakvilla.beatzmedia.studio.application.port.in.ListStudioEpisodes;
import org.shakvilla.beatzmedia.studio.application.port.in.ListStudioPodcastShows;
import org.shakvilla.beatzmedia.studio.application.port.in.PodcastShowView;
import org.shakvilla.beatzmedia.studio.application.port.in.SetShowCover;
import org.shakvilla.beatzmedia.studio.application.port.in.UpdateEpisode;
import org.shakvilla.beatzmedia.studio.application.port.in.UpdateEpisode.UpdateEpisodeCommand;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.ShowId;

/**
 * Thin REST resource for the Studio podcast shows/episodes endpoints (LLFR-STUDIO-02.1 – 02.4).
 * Maps HTTP to input ports; no business logic here. Studio ADD §5.1 / API-CONTRACT.md §11.
 *
 * <ul>
 *   <li>GET /v1/studio/podcasts/shows → 200 {@code StudioPodcastShowDto[]}
 *   <li>POST /v1/studio/podcasts/shows → 201 {@code StudioPodcastShowDto}
 *   <li>GET /v1/studio/podcasts/episodes → 200 {@code StudioEpisodeDto[]}
 *   <li>POST /v1/studio/podcasts/episodes → 201 {@code StudioEpisodeDto} (multipart, requires
 *       {@code Idempotency-Key})
 *   <li>PATCH /v1/studio/podcasts/episodes/:id → 200 {@code StudioEpisodeDto}
 *   <li>DELETE /v1/studio/podcasts/episodes/:id → 204
 * </ul>
 *
 * <p>Auth: every endpoint requires {@code roles ∋ artist} (else 403), enforced with {@code
 * @RolesAllowed} — same mechanism as {@code StudioProfileResource} (WU-STU-1). Ownership is
 * re-checked in the application layer for every show/episode read and mutation.
 */
@Path("/v1/studio/podcasts")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("artist")
public class StudioPodcastResource {

  private final ListStudioPodcastShows listStudioPodcastShows;
  private final CreatePodcastShow createPodcastShow;
  private final ListStudioEpisodes listStudioEpisodes;
  private final CreateEpisode createEpisode;
  private final UpdateEpisode updateEpisode;
  private final DeleteEpisode deleteEpisode;
  private final SetShowCover setShowCover;
  private final JsonWebToken jwt;

  @Inject
  public StudioPodcastResource(
      ListStudioPodcastShows listStudioPodcastShows,
      CreatePodcastShow createPodcastShow,
      ListStudioEpisodes listStudioEpisodes,
      CreateEpisode createEpisode,
      UpdateEpisode updateEpisode,
      DeleteEpisode deleteEpisode,
      SetShowCover setShowCover,
      JsonWebToken jwt) {
    this.listStudioPodcastShows = listStudioPodcastShows;
    this.createPodcastShow = createPodcastShow;
    this.listStudioEpisodes = listStudioEpisodes;
    this.createEpisode = createEpisode;
    this.updateEpisode = updateEpisode;
    this.deleteEpisode = deleteEpisode;
    this.setShowCover = setShowCover;
    this.jwt = jwt;
  }

  // ---- Shows ----

  /** GET /v1/studio/podcasts/shows — LLFR-STUDIO-02.1. */
  @GET
  @Path("/shows")
  public List<PodcastShowView> listShows() {
    return listStudioPodcastShows.list(artistId());
  }

  /** POST /v1/studio/podcasts/shows — LLFR-STUDIO-02.1. */
  @POST
  @Path("/shows")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createShow(CreateShowBody body) {
    PodcastShowView view = createPodcastShow.create(
        artistId(),
        new CreatePodcastShowCommand(
            body != null ? body.title() : null,
            body != null ? body.category() : null,
            body != null ? body.image() : null,
            body != null ? body.description() : null));
    return Response.status(Response.Status.CREATED).entity(view).build();
  }

  /**
   * POST /v1/studio/podcasts/shows/:id/cover — multipart {@code image} part.
   *
   * <p>Returns {@code { "image": "/v1/media/images/{assetId}" }}, the stable URL now stored on the
   * show. A show cannot publish an episode to fans without one, because the fan-facing
   * {@code podcast.image} column is NOT NULL.
   */
  @POST
  @Path("/shows/{id}/cover")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response setShowCover(@PathParam("id") String id, @MultipartForm ShowCoverForm form) {
    if (form == null || form.image == null) {
      throw new ValidationException("'image' file part is required", "image");
    }
    String url = setShowCover.setCover(artistId(), new ShowId(id), toImageUpload(form.image));
    return Response.ok(java.util.Map.of("image", url)).build();
  }

  // ---- Episodes ----

  /** GET /v1/studio/podcasts/episodes — LLFR-STUDIO-02.2. */
  @GET
  @Path("/episodes")
  public List<EpisodeView> listEpisodes() {
    return listStudioEpisodes.list(artistId());
  }

  /**
   * POST /v1/studio/podcasts/episodes — LLFR-STUDIO-02.3. Multipart: {@code audio} file part +
   * {@code data} JSON part. Requires {@code Idempotency-Key}.
   */
  @POST
  @Path("/episodes")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response createEpisode(
      @HeaderParam("Idempotency-Key") String idempotencyKey, @MultipartForm CreateEpisodeForm form) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ValidationException("Idempotency-Key header is required", "Idempotency-Key");
    }
    CreateEpisodeBody body = form.data;
    if (body == null) {
      throw new ValidationException("'data' JSON part is required", "data");
    }

    AudioUpload audio = toAudioUpload(form.audio);
    CreateEpisodeCommand cmd = toCommand(body);

    EpisodeView view = createEpisode.create(artistId(), idempotencyKey, cmd, audio);
    return Response.status(Response.Status.CREATED).entity(view).build();
  }

  /** PATCH /v1/studio/podcasts/episodes/:id — LLFR-STUDIO-02.4. */
  @PATCH
  @Path("/episodes/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  public EpisodeView update(@PathParam("id") String id, UpdateEpisodeBody body) {
    UpdateEpisodeCommand cmd = new UpdateEpisodeCommand(
        body.title(), body.description(), body.premium(), body.price(), body.visibility(),
        parseInstant(body.date(), "date"), body.earlyAccess());
    return updateEpisode.update(artistId(), new EpisodeId(id), cmd);
  }

  /** DELETE /v1/studio/podcasts/episodes/:id — LLFR-STUDIO-02.4. */
  @DELETE
  @Path("/episodes/{id}")
  public Response delete(@PathParam("id") String id) {
    deleteEpisode.delete(artistId(), new EpisodeId(id));
    return Response.noContent().build();
  }

  // ---- Helpers ----

  private ArtistId artistId() {
    return new ArtistId(jwt.getSubject());
  }

  private CreateEpisodeCommand toCommand(CreateEpisodeBody body) {
    return new CreateEpisodeCommand(
        body.showId(),
        body.newShow() != null ? body.newShow().title() : null,
        body.newShow() != null ? body.newShow().category() : null,
        body.title(),
        body.description(),
        body.cover(),
        body.visibility(),
        parseInstant(body.date(), "date"),
        body.premium(),
        body.price(),
        body.earlyAccess() != null && body.earlyAccess());
  }

  private static Instant parseInstant(String value, String field) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (Exception e) {
      throw new ValidationException(field + " must be an ISO-8601 timestamp", field);
    }
  }

  /** Same hash-then-restream shape as {@link #toAudioUpload}: media needs the digest up front. */
  private static SetShowCover.ImageUpload toImageUpload(FileUpload file) {
    try (InputStream body = java.nio.file.Files.newInputStream(file.uploadedFile())) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      DigestInputStream digestBody = new DigestInputStream(body, digest);
      byte[] buf = new byte[8192];
      //noinspection StatementWithEmptyBody
      while (digestBody.read(buf) != -1) {}
      String contentHash = bytesToHex(digest.digest());
      return new SetShowCover.ImageUpload(
          file.fileName(),
          file.contentType(),
          file.size(),
          java.nio.file.Files.newInputStream(file.uploadedFile()),
          contentHash);
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("Could not read the uploaded image", e);
    }
  }

  private static AudioUpload toAudioUpload(FileUpload file) {
    if (file == null) {
      return null;
    }
    String filename = file.fileName();
    String contentType = file.contentType();
    long size = file.size();
    try (InputStream body = java.nio.file.Files.newInputStream(file.uploadedFile())) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      DigestInputStream digestBody = new DigestInputStream(body, digest);
      byte[] buf = new byte[8192];
      //noinspection StatementWithEmptyBody
      while (digestBody.read(buf) != -1) {}
      String contentHash = bytesToHex(digest.digest());
      InputStream uploadBody = java.nio.file.Files.newInputStream(file.uploadedFile());
      return new AudioUpload(filename, contentType, size, uploadBody, contentHash);
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new RuntimeException("Failed to read uploaded audio file", e);
    }
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  // ---- Request DTOs (records) ----

  /** {@code CreateShowDto {title,category}} — Studio ADD §5.1. */
  /** {@code image} is the show cover — optional here, required before an episode can publish. */
  public record CreateShowBody(String title, String category, String image, String description) {}

  /**
   * {@code CreateEpisodeDto} — Studio ADD §6. {@code showId} XOR {@code newShow}; {@code cover} is
   * a plain URL string (not a second file part); {@code visibility} is {@code public|scheduled};
   * {@code date} is ISO-8601, required and strictly future when {@code visibility=scheduled}.
   */
  public record CreateEpisodeBody(
      String showId,
      NewShowBody newShow,
      String title,
      String description,
      String cover,
      String visibility,
      String date,
      boolean premium,
      BigDecimal price,
      Boolean earlyAccess) {}

  public record NewShowBody(String title, String category) {}

  /** {@code UpdateEpisodeDto} — Studio ADD §6. All fields optional (PATCH semantics). */
  public record UpdateEpisodeBody(
      String title,
      String description,
      Boolean premium,
      BigDecimal price,
      String visibility,
      String date,
      Boolean earlyAccess) {}
}
