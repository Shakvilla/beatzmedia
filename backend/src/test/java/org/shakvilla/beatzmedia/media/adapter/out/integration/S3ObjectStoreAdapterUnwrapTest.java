package org.shakvilla.beatzmedia.media.adapter.out.integration;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.domain.FileTooLargeException;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Unit test for the adapter's exception translation around the S3 PUT.
 *
 * <p>Security review of WU-MED-1 (PR #11): the 500MB cap is enforced by a limiting input stream
 * that throws {@link FileTooLargeException} mid-read. When the real AWS SDK reads that stream inside
 * its marshalling loop and the exception is thrown, the SDK may wrap it as {@link
 * SdkClientException}. A naive adapter would then leak the SDK exception, and {@code
 * DomainExceptionMapper} — which only handles {@code DomainException} — would return HTTP 500
 * instead of 413. The adapter must unwrap the domain exception so oversize uploads reliably surface
 * 413.
 *
 * <p>This drives the fix without a real S3/MinIO: a stub {@link S3Client} throws on {@code
 * putObject}, simulating the SDK wrapping behavior deterministically.
 */
class S3ObjectStoreAdapterUnwrapTest {

  private static S3ObjectStoreAdapter adapterWithPutObjectThrowing(RuntimeException toThrow) {
    InvocationHandler handler =
        (proxy, method, args) -> {
          if ("putObject".equals(method.getName())) {
            throw toThrow;
          }
          // Object methods + close() etc. — return a harmless default.
          if ("toString".equals(method.getName())) {
            return "stub-s3-client";
          }
          return null;
        };
    S3Client stub =
        (S3Client)
            Proxy.newProxyInstance(
                S3Client.class.getClassLoader(), new Class<?>[] {S3Client.class}, handler);
    // presigner is not exercised by putOriginal — null is fine for this unit test.
    return new S3ObjectStoreAdapter(stub, null, "originals", "delivery");
  }

  /** SDK wraps the size-cap exception → adapter must surface the original {@link FileTooLargeException}. */
  @Test
  void wrapped_file_too_large_is_unwrapped_to_413_domain_exception() {
    FileTooLargeException original = new FileTooLargeException("File exceeds maximum allowed size");
    SdkClientException wrapped =
        SdkClientException.builder().message("Unable to execute HTTP request").cause(original).build();
    S3ObjectStoreAdapter adapter = adapterWithPutObjectThrowing(wrapped);

    FileTooLargeException thrown =
        assertThrows(
            FileTooLargeException.class,
            () ->
                adapter.putOriginal(
                    MediaKind.AUDIO,
                    new MediaAssetId("u-1"),
                    new ByteArrayInputStream(new byte[16]),
                    "audio/wav",
                    16L));

    assertSame(original, thrown, "the original domain exception must be re-surfaced, not a copy");
  }

  /** Deeply nested cause chain is still unwrapped. */
  @Test
  void deeply_nested_file_too_large_is_unwrapped() {
    FileTooLargeException original = new FileTooLargeException("File exceeds maximum allowed size");
    SdkClientException wrapped =
        SdkClientException.builder()
            .message("outer")
            .cause(new RuntimeException("middle", original))
            .build();
    S3ObjectStoreAdapter adapter = adapterWithPutObjectThrowing(wrapped);

    assertThrows(
        FileTooLargeException.class,
        () ->
            adapter.putOriginal(
                MediaKind.AUDIO,
                new MediaAssetId("u-2"),
                new ByteArrayInputStream(new byte[16]),
                "audio/wav",
                16L));
  }

  /** A genuine SDK failure (no domain cause) must propagate unchanged — no over-broad swallowing. */
  @Test
  void unrelated_sdk_exception_propagates_unchanged() {
    SdkClientException sdkFailure =
        SdkClientException.builder()
            .message("connection reset")
            .cause(new IOException("connection reset by peer"))
            .build();
    S3ObjectStoreAdapter adapter = adapterWithPutObjectThrowing(sdkFailure);

    SdkClientException thrown =
        assertThrows(
            SdkClientException.class,
            () ->
                adapter.putOriginal(
                    MediaKind.AUDIO,
                    new MediaAssetId("u-3"),
                    new ByteArrayInputStream(new byte[16]),
                    "audio/wav",
                    16L));
    assertSame(sdkFailure, thrown);
  }
}
