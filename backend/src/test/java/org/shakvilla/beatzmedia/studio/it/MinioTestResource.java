package org.shakvilla.beatzmedia.studio.it;

import java.net.URI;
import java.util.Map;

import org.testcontainers.containers.MinIOContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Quarkus test resource: starts a Testcontainers MinIO container and pre-creates the two buckets
 * the media pipeline expects ({@code beatz-media-originals}/{@code beatz-media-delivery}), then
 * overrides {@code beatz.s3.*} config so the real {@code S3ObjectStoreAdapter} (via {@code
 * S3ClientProducer}) talks to it. {@code S3ObjectStoreAdapter} does not auto-create buckets (see
 * {@code media.it.MediaDeliveryIT}/{@code RealTranscodeIT}, which create them the same way) — this
 * is the {@code @QuarkusTestResource} equivalent for a full {@code @QuarkusTest} REST flow, needed
 * because {@code POST /studio/podcasts/episodes} exercises the real
 * {@code UploadOriginalUseCase} end to end (Studio ADD §11 testing plan).
 *
 * <p>Used only by {@code studio.it} tests that need a real, successful media upload (create-episode
 * happy paths, idempotency replay, scheduler go-live fixtures) — negative-path tests that never
 * reach the media pipeline (e.g. unsupported audio content type) do not need this resource, mirroring
 * {@code catalog.StudioReleaseResourceIT}'s existing convention of testing only the error path at
 * {@code @QuarkusTest} level for uploads that DO reach S3.
 */
public class MinioTestResource implements QuarkusTestResourceLifecycleManager {

  private static final String BUCKET_ORIGINALS = "beatz-media-originals";
  private static final String BUCKET_DELIVERY = "beatz-media-delivery";

  private MinIOContainer minio;

  @Override
  public Map<String, String> start() {
    minio = new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z")
        .withUserName("minioadmin")
        .withPassword("minioadmin");
    minio.start();

    URI endpoint = URI.create(minio.getS3URL());
    S3Client s3Client = S3Client.builder()
        .endpointOverride(endpoint)
        .region(Region.US_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
    try {
      s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_ORIGINALS).build());
      s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_DELIVERY).build());
    } finally {
      s3Client.close();
    }

    return Map.of(
        "beatz.s3.endpoint", minio.getS3URL(),
        "beatz.s3.access-key", minio.getUserName(),
        "beatz.s3.secret-key", minio.getPassword(),
        "beatz.s3.bucket-originals", BUCKET_ORIGINALS,
        "beatz.s3.bucket-delivery", BUCKET_DELIVERY);
  }

  @Override
  public void stop() {
    if (minio != null) {
      minio.stop();
    }
  }
}
