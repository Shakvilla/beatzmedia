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
   * @return the resulting {@link ObjectKey}
   */
  ObjectKey putOriginal(MediaKind kind, MediaAssetId id, InputStream body, String contentType);

  /**
   * Write a delivery-side file (HLS segment, playlist, artwork variant) to the delivery bucket.
   *
   * @param relativeKey path within delivery bucket (e.g. "delivery/{id}/hls/playlist.m3u8")
   * @return the resulting {@link ObjectKey}
   */
  ObjectKey putDelivery(MediaAssetId id, String relativeKey, InputStream body, String contentType);

  /** Check if an object exists (used for idempotency guard on re-upload). */
  boolean exists(ObjectKey key);
}
