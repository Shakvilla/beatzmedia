package org.shakvilla.beatzmedia.media.adapter.in.rest;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import org.shakvilla.beatzmedia.media.application.port.in.ReadArtworkUseCase;
import org.shakvilla.beatzmedia.media.application.port.out.ObjectStorePort.StoredObject;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;

/**
 * Public delivery for cover images.
 *
 * <p><strong>Why images are streamed and audio is signed.</strong> A presigned URL expires. That is
 * correct for a stream a fan requests on demand, and wrong for a cover: {@code podcast.image} is
 * written once and rendered on a browse card from then on, so an expiring URL would turn every card
 * into a broken image the moment its TTL passed. This endpoint gives a stable, cacheable address.
 *
 * <p><strong>The CDN seam.</strong> When images move to ImageKit this method returns a 302 instead
 * of a stream and every stored URL keeps working unchanged — which is why the stored value is this
 * path and not a bucket URL.
 *
 * <p>{@code @PermitAll}: cover art is public by definition, shown on browse pages to signed-out
 * visitors. The use case still refuses to serve anything that is not a processed ARTWORK asset, so
 * an id cannot be used to pull master audio out of the bucket.
 */
@Path("/v1/media/images")
@PermitAll
public class MediaImageResource {

  /** A cover changes only when the artist replaces it, and a replacement gets a new asset id. */
  private static final int CACHE_SECONDS = 60 * 60 * 24 * 30;

  private final ReadArtworkUseCase readArtwork;

  @Inject
  public MediaImageResource(ReadArtworkUseCase readArtwork) {
    this.readArtwork = readArtwork;
  }

  /** GET /v1/media/images/:assetId — 404 when unknown, not artwork, or still processing. */
  @GET
  @Path("/{assetId}")
  public Response get(@PathParam("assetId") String assetId) {
    StoredObject object = readArtwork.openArtwork(new MediaAssetId(assetId));

    CacheControl cache = new CacheControl();
    cache.setMaxAge(CACHE_SECONDS);
    cache.setPrivate(false);

    StreamingOutput body = out -> {
      try (var in = object.body()) {
        in.transferTo(out);
      }
    };

    Response.ResponseBuilder response =
        Response.ok(body, object.contentType()).cacheControl(cache);
    if (object.sizeBytes() >= 0) {
      response.header("Content-Length", object.sizeBytes());
    }
    return response.build();
  }
}
