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
   * Write a delivery-side file (HLS segment, playlist, artwork variant) to the delivery bucket.
   *
   * @param relativeKey path within delivery bucket (e.g. "delivery/{id}/hls/playlist.m3u8")
   * @return the resulting {@link ObjectKey}
   */
  ObjectKey putDelivery(MediaAssetId id, String relativeKey, InputStream body, String contentType);

  /** Check if an object exists (used for idempotency guard on re-upload). */
  boolean exists(ObjectKey key);

  /**
   * Delete an object from the store. Used after virus-scan rejection to purge malicious originals.
   * Implementations must treat a missing key as a no-op (idempotent delete). H-1.
   */
  void deleteOriginal(ObjectKey key);
}
