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
  private final String accessKey;
  private final String secretKey;

  public S3ClientProducer(
      @ConfigProperty(name = "beatz.s3.endpoint", defaultValue = "http://localhost:9000")
          String endpoint,
      @ConfigProperty(name = "beatz.s3.access-key", defaultValue = "minioadmin")
          String accessKey,
      @ConfigProperty(name = "beatz.s3.secret-key", defaultValue = "minioadmin")
          String secretKey) {
    this.endpoint = endpoint;
    this.accessKey = accessKey;
    this.secretKey = secretKey;
  }

  @Produces
  @Singleton
  public S3Client s3Client() {
    return S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.US_EAST_1) // required by SDK; MinIO ignores it
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
        .serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }

  @Produces
  @Singleton
  public S3Presigner s3Presigner() {
    return S3Presigner.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.US_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
        .serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }
}
