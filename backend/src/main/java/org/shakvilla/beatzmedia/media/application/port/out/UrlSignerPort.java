package org.shakvilla.beatzmedia.media.application.port.out;

import java.time.Duration;

import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;
import org.shakvilla.beatzmedia.media.domain.SignedUrl;

/** Output port for presigning delivery URLs (S3/MinIO). ADD §4.2 / §5.2. */
public interface UrlSignerPort {

  /**
   * Produce a time-boxed, pre-signed GET URL for the given delivery key.
   *
   * @param key     the delivery-bucket object to sign
   * @param variant FULL or PREVIEW (carried into the result for INV-3 auditability)
   * @param ttl     how long the URL is valid
   * @return {@link SignedUrl} with url + expiresAt
   */
  SignedUrl presignGet(ObjectKey key, DeliveryVariant variant, Duration ttl);
}
