package org.shakvilla.beatzmedia.media.adapter.out.integration;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.media.application.port.out.ObjectStorePort;
import org.shakvilla.beatzmedia.media.application.port.out.UrlSignerPort;
import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;
import org.shakvilla.beatzmedia.media.domain.SignedUrl;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * S3/MinIO adapter implementing {@link ObjectStorePort} and {@link UrlSignerPort}.
 * Originals bucket is PRIVATE — never signed for client read. ADD §3 / §5.2 / ADR (WU-MED-1 §3).
 */
@ApplicationScoped
public class S3ObjectStoreAdapter implements ObjectStorePort, UrlSignerPort {

  private static final String ORIGINALS_KEY_PREFIX = "originals/";

  private final S3Client s3Client;
  private final S3Presigner presigner;
  private final String bucketOriginals;
  private final String bucketDelivery;

  @Inject
  public S3ObjectStoreAdapter(
      S3Client s3Client,
      S3Presigner presigner,
      @ConfigProperty(name = "beatz.s3.bucket-originals", defaultValue = "beatz-media-originals")
          String bucketOriginals,
      @ConfigProperty(name = "beatz.s3.bucket-delivery", defaultValue = "beatz-media-delivery")
          String bucketDelivery) {
    this.s3Client = s3Client;
    this.presigner = presigner;
    this.bucketOriginals = bucketOriginals;
    this.bucketDelivery = bucketDelivery;
  }

  // ---- ObjectStorePort ----

  @Override
  public ObjectKey putOriginal(
      MediaKind kind, MediaAssetId id, InputStream body, String contentType) {
    String key = ORIGINALS_KEY_PREFIX + kind.name().toLowerCase() + "/" + id.value();
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(bucketOriginals)
            .key(key)
            .contentType(contentType)
            .build();
    s3Client.putObject(request, toRequestBody(body));
    return new ObjectKey(bucketOriginals, key);
  }

  @Override
  public ObjectKey putDelivery(
      MediaAssetId id, String relativeKey, InputStream body, String contentType) {
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(bucketDelivery)
            .key(relativeKey)
            .contentType(contentType)
            .build();
    s3Client.putObject(request, toRequestBody(body));
    return new ObjectKey(bucketDelivery, relativeKey);
  }

  /** Read the stream fully into memory so we can supply a known content-length to the SDK. */
  private RequestBody toRequestBody(InputStream body) {
    try {
      byte[] bytes = body.readAllBytes();
      return RequestBody.fromBytes(bytes);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read upload body into memory", e);
    }
  }

  @Override
  public boolean exists(ObjectKey key) {
    try {
      s3Client.headObject(
          HeadObjectRequest.builder().bucket(key.bucket()).key(key.key()).build());
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    }
  }

  // ---- UrlSignerPort ----

  @Override
  public SignedUrl presignGet(ObjectKey key, DeliveryVariant variant, Duration ttl) {
    Instant expiresAt = Instant.now().plus(ttl);

    PresignedGetObjectRequest presigned =
        presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(r -> r.bucket(key.bucket()).key(key.key()))
                .build());

    return new SignedUrl(presigned.url().toString(), variant, expiresAt);
  }
}
