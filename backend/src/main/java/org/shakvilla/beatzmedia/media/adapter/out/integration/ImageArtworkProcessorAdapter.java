package org.shakvilla.beatzmedia.media.adapter.out.integration;

import java.io.IOException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.media.application.port.out.ArtworkProcessorPort;
import org.shakvilla.beatzmedia.media.application.port.out.ObjectStorePort;
import org.shakvilla.beatzmedia.media.application.service.MagicByteValidator;
import org.shakvilla.beatzmedia.media.domain.ImageFormat;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Artwork processor — validates image format by magic bytes and copies the original as the
 * primary delivery variant. v1 ships without resizing; the port interface allows richer variants
 * to be added without touching consumers. ADD §5.2.
 */
@ApplicationScoped
public class ImageArtworkProcessorAdapter implements ArtworkProcessorPort {

  private static final int MAGIC_PROBE = 8;

  private final S3Client s3Client;
  private final ObjectStorePort objectStore;
  private final MagicByteValidator validator;
  private final String bucketDelivery;

  @Inject
  public ImageArtworkProcessorAdapter(
      S3Client s3Client,
      ObjectStorePort objectStore,
      MagicByteValidator validator,
      @ConfigProperty(name = "beatz.s3.bucket-delivery", defaultValue = "beatz-media-delivery")
          String bucketDelivery) {
    this.s3Client = s3Client;
    this.objectStore = objectStore;
    this.validator = validator;
    this.bucketDelivery = bucketDelivery;
  }

  @Override
  public ImageFormat detectFormat(ObjectKey original) {
    byte[] header = downloadHeader(original, MAGIC_PROBE);
    return validator.detectImageFormat(header);
  }

  @Override
  public ObjectKey processVariants(ObjectKey original, MediaAssetId id) {
    ImageFormat fmt = detectFormat(original);
    String ext = fmt == ImageFormat.PNG ? "png" : "jpg";
    String artKey = "delivery/" + id.value() + "/art/cover-1024." + ext;
    String contentType = fmt == ImageFormat.PNG ? "image/png" : "image/jpeg";

    // Download the original and re-upload as the primary art variant
    GetObjectRequest req =
        GetObjectRequest.builder().bucket(original.bucket()).key(original.key()).build();
    try (ResponseInputStream<GetObjectResponse> resp = s3Client.getObject(req)) {
      objectStore.putDelivery(id, artKey, resp, contentType);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to process artwork for " + id.value(), e);
    }
    return new ObjectKey(deliveryBucketOf(original), artKey);
  }

  private byte[] downloadHeader(ObjectKey key, int maxBytes) {
    GetObjectRequest req =
        GetObjectRequest.builder().bucket(key.bucket()).key(key.key()).build();
    try (ResponseInputStream<GetObjectResponse> resp = s3Client.getObject(req)) {
      byte[] buf = new byte[maxBytes];
      int read = resp.readNBytes(buf, 0, maxBytes);
      return (read < maxBytes) ? java.util.Arrays.copyOf(buf, read) : buf;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read artwork header for " + key.key(), e);
    }
  }

  private String deliveryBucketOf(ObjectKey original) {
    // S3: use the injected delivery bucket name — never derive from originals bucket string. S3.
    return bucketDelivery;
  }
}
