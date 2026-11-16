package org.shakvilla.beatzmedia.media.adapter.out.integration;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * CDI producer for {@link S3Client} and {@link S3Presigner} configured from {@code beatz.s3.*}
 * properties. Uses path-style access required by MinIO. ADD §5.2 / ADR (WU-MED-1 §3).
 */
@ApplicationScoped
public class S3ClientProducer {

  private final String endpoint;
  private final String publicEndpoint;
  private final String accessKey;
  private final String secretKey;
  private final String s4Endpoint;
  private final String s4PublicEndpoint;
  private final String s4Region;
  private final String s4AccessKey;
  private final String s4SecretKey;

  public S3ClientProducer(
      @ConfigProperty(name = "beatz.s3.endpoint", defaultValue = "http://localhost:9000")
          String endpoint,
      /*
       * The endpoint the CLIENT will fetch from, which is not always the one the server uses.
       * Under Compose the app reaches MinIO at http://objectstore:9000 (a Docker network alias),
       * and presigning with it produced audioUrls no browser could resolve — playback failed at
       * DNS. The URL cannot be patched client-side either: SigV4 signs the host header, so
       * rewriting objectstore->localhost invalidates the signature (verified: 403).
       *
       * Defaults to beatz.s3.endpoint, so single-host and production setups are unaffected.
       */
      @ConfigProperty(name = "beatz.s3.public-endpoint") java.util.Optional<String> publicEndpoint,
      @ConfigProperty(name = "beatz.s3.access-key", defaultValue = "minioadmin")
          String accessKey,
      @ConfigProperty(name = "beatz.s3.secret-key", defaultValue = "minioadmin")
          String secretKey,
      @ConfigProperty(name = "beatz.s4.endpoint") java.util.Optional<String> s4Endpoint,
      @ConfigProperty(name = "beatz.s4.public-endpoint") java.util.Optional<String> s4PublicEndpoint,
      @ConfigProperty(name = "beatz.s4.region") java.util.Optional<String> s4Region,
      @ConfigProperty(name = "beatz.s4.access-key") java.util.Optional<String> s4AccessKey,
      @ConfigProperty(name = "beatz.s4.secret-key") java.util.Optional<String> s4SecretKey) {
    this.endpoint = endpoint;
    this.publicEndpoint = publicEndpoint.filter(e -> !e.isBlank()).orElse(endpoint);
    this.accessKey = accessKey;
    this.secretKey = secretKey;
    this.s4Endpoint = configured(s4Endpoint, endpoint);
    this.s4PublicEndpoint = configured(s4PublicEndpoint, this.s4Endpoint);
    this.s4Region = configured(s4Region, "us-east-1");
    this.s4AccessKey = configured(s4AccessKey, accessKey);
    this.s4SecretKey = configured(s4SecretKey, secretKey);
  }

  private static String configured(java.util.Optional<String> value, String fallback) {
    return value.filter(v -> !v.isBlank()).orElse(fallback);
  }

  @Produces
  @Singleton
  @StorageClient("minio")
  public S3Client s3Client() {
    return client(endpoint, Region.US_EAST_1, accessKey, secretKey);
  }

  @Produces
  @Singleton
  @StorageClient("mega_s4")
  public S3Client megaS4Client() {
    return client(s4Endpoint, Region.of(s4Region), s4AccessKey, s4SecretKey);
  }

  private static S3Client client(String endpoint, Region region, String key, String secret) {
    return S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(region)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret)))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }

  @Produces
  @Singleton
  @StorageClient("minio")
  public S3Presigner s3Presigner() {
    // Presigned URLs are handed to browsers, so they must be signed for the endpoint the browser
    // can actually reach — see the publicEndpoint note above.
    return presigner(publicEndpoint, Region.US_EAST_1, accessKey, secretKey);
  }

  @Produces
  @Singleton
  @StorageClient("mega_s4")
  public S3Presigner megaS4Presigner() {
    return presigner(s4PublicEndpoint, Region.of(s4Region), s4AccessKey, s4SecretKey);
  }

  private static S3Presigner presigner(String endpoint, Region region, String key, String secret) {
    return S3Presigner.builder()
        .endpointOverride(URI.create(endpoint))
        .region(region)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret)))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }
}
