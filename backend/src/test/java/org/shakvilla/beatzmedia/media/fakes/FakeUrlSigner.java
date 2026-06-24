package org.shakvilla.beatzmedia.media.fakes;

import java.time.Duration;
import java.time.Instant;

import org.shakvilla.beatzmedia.media.application.port.out.UrlSignerPort;
import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;
import org.shakvilla.beatzmedia.media.domain.SignedUrl;

/** In-memory fake for {@link UrlSignerPort}. Produces deterministic URLs for unit tests. */
public class FakeUrlSigner implements UrlSignerPort {

  private static final Instant FIXED_BASE = Instant.parse("2026-01-01T00:00:00Z");

  @Override
  public SignedUrl presignGet(ObjectKey key, DeliveryVariant variant, Duration ttl) {
    String url = "https://fake-s3/" + key.bucket() + "/" + key.key() + "?sig=fake";
    return new SignedUrl(url, variant, FIXED_BASE.plus(ttl));
  }
}
