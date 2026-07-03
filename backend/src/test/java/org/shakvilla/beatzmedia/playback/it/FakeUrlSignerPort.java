package org.shakvilla.beatzmedia.playback.it;

import java.time.Duration;
import java.time.Instant;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import org.shakvilla.beatzmedia.media.application.port.out.UrlSignerPort;
import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;
import org.shakvilla.beatzmedia.media.domain.SignedUrl;

/**
 * Test-only {@link UrlSignerPort} that replaces the production {@code S3ObjectStoreAdapter}
 * across the whole test application (a CDI {@link Alternative} with the highest {@link Priority}),
 * so playback's REST-assured integration tests exercise the real media
 * {@code IssueDeliveryUrlUseCase}/{@code MediaService} call chain end-to-end without requiring a
 * live S3/MinIO endpoint (mirrors payments' {@code CountingPaymentGateway} pattern). Deterministically
 * echoes the requested key + variant into the URL so tests can assert which rendition was actually
 * signed (INV-3 proof at the REST boundary).
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FakeUrlSignerPort implements UrlSignerPort {

  @Override
  public SignedUrl presignGet(ObjectKey key, DeliveryVariant variant, Duration ttl) {
    String url =
        "https://cdn.test/" + key.bucket() + "/" + key.key() + "?variant=" + variant.name();
    return new SignedUrl(url, variant, Instant.now().plus(ttl));
  }
}
