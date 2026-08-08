package org.shakvilla.beatzmedia.media.application.port.out;

import java.io.InputStream;

import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

/** Output port for S3/MinIO object storage. ADD §4.2 / §5.2. */
public interface ObjectStorePort {

  /**
   * Stream a raw upload to the private originals bucket.
   *
   * @param kind        the media kind (used to build the object key prefix)
   * @param id          the asset id
   * @param body        the raw bytes stream (streamed, not buffered)
   * @param contentType MIME type for the S3 object
   * @param contentLength exact byte count when known; pass {@code -1} when the length is unknown or
   *     untrusted, and the adapter will spool the body to determine it
   * @return the resulting {@link ObjectKey}
   */
  ObjectKey putOriginal(
      MediaKind kind, MediaAssetId id, InputStream body, String contentType, long contentLength);

  /**
   * Write a delivery-side file (audio rendition, artwork variant) to the delivery bucket.
   *
   * @param relativeKey path within delivery bucket (e.g. "delivery/{id}/full.m4a")
   * @return the resulting {@link ObjectKey}
   */
  ObjectKey putDelivery(MediaAssetId id, String relativeKey, InputStream body, String contentType);

  /** Check if an object exists (used for idempotency guard on re-upload). */
  boolean exists(ObjectKey key);

  /**
   * Opens a stored object for reading.
   *
   * <p>Needed because cover images are served through the app rather than signed. A presigned URL
   * is time-boxed, which suits a 30-second audio stream but not {@code podcast.image}, which sits
   * on a browse card indefinitely and is rendered by every fan.
   *
   * @return the object's bytes and content type; the caller must close the stream
   */
  StoredObject open(ObjectKey key);

  /** An object's bytes plus the content type to echo back. */
  record StoredObject(java.io.InputStream body, String contentType, long sizeBytes) {}

  /**
   * Delete an object from the store. Used after virus-scan rejection to purge malicious originals.
   * Implementations must treat a missing key as a no-op (idempotent delete). H-1.
   */
  void deleteOriginal(ObjectKey key);
}
