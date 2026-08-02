package org.shakvilla.beatzmedia.media.adapter.out.integration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.media.application.port.out.AudioTranscoderPort;
import org.shakvilla.beatzmedia.media.application.port.out.ObjectStorePort;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * ffmpeg/ffprobe-based implementation of {@link AudioTranscoderPort}. Invokes ffprobe and ffmpeg
 * via {@link ProcessBuilder} (shell-out). Downloads the original from S3/MinIO to a temp file,
 * transcodes to a single AAC/M4A full rendition and a single AAC/M4A ≤30s preview rendition,
 * uploads each back, cleans up temp files.
 * ADD §5.2 / ADR (WU-MED-1 §2).
 */
@ApplicationScoped
public class FfmpegAudioTranscoderAdapter implements AudioTranscoderPort {

  private final S3Client s3Client;
  private final ObjectStorePort objectStore;
  private final String bucketDelivery;

  @Inject
  public FfmpegAudioTranscoderAdapter(
      S3Client s3Client,
      ObjectStorePort objectStore,
      @ConfigProperty(name = "beatz.s3.bucket-delivery", defaultValue = "beatz-media-delivery")
          String bucketDelivery) {
    this.s3Client = s3Client;
    this.objectStore = objectStore;
    this.bucketDelivery = bucketDelivery;
  }

  @Override
  public int probeDurationSec(ObjectKey original) {
    Path tmpInput = null;
    try {
      tmpInput = downloadToTemp(original, "probe-", ".audio");
      return runFfprobe(tmpInput);
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ffprobe failed for " + original.key(), e);
    } finally {
      deleteSilently(tmpInput);
    }
  }

  @Override
  public ObjectKey transcodeFull(ObjectKey original, MediaAssetId id) {
    return transcodeToM4a(original, id, null, "full.m4a", "full-");
  }

  @Override
  public ObjectKey clipPreview(ObjectKey original, MediaAssetId id, int previewSeconds) {
    return transcodeToM4a(original, id, previewSeconds, "preview.m4a", "preview-");
  }

  /**
   * Shared path for both renditions: download the original, run ffmpeg to one AAC/M4A file,
   * upload it, return its key. {@code durationLimit} non-null clips the output (preview).
   */
  private ObjectKey transcodeToM4a(
      ObjectKey original, MediaAssetId id, Integer durationLimit, String filename, String tmpPrefix) {
    Path tmpInput = null;
    Path tmpOutput = null;
    try {
      tmpInput = downloadToTemp(original, tmpPrefix, ".audio");
      tmpOutput = Files.createTempFile(tmpPrefix + id.value(), ".m4a");

      runFfmpegM4a(tmpInput, tmpOutput, durationLimit);

      String relKey = "delivery/" + id.value() + "/" + filename;
      try (InputStream in = Files.newInputStream(tmpOutput)) {
        objectStore.putDelivery(id, relKey, in, "audio/mp4");
      }
      return new ObjectKey(deliveryBucketOf(original), relKey);
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ffmpeg transcode failed for " + id.value(), e);
    } finally {
      deleteSilently(tmpOutput);
      deleteSilently(tmpInput);
    }
  }

  // ---- Private helpers ----

  private Path downloadToTemp(ObjectKey key, String prefix, String suffix) throws IOException {
    Path tmp = Files.createTempFile(prefix + key.key().replace("/", "_"), suffix);
    GetObjectRequest req = GetObjectRequest.builder().bucket(key.bucket()).key(key.key()).build();
    try (ResponseInputStream<GetObjectResponse> resp = s3Client.getObject(req)) {
      Files.copy(resp, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    return tmp;
  }

  private int runFfprobe(Path inputFile) throws IOException, InterruptedException {
    List<String> cmd = new ArrayList<>();
    cmd.add("ffprobe");
    cmd.add("-v"); cmd.add("quiet");
    cmd.add("-print_format"); cmd.add("compact=print_section=0:nokey=1:escape=csv");
    cmd.add("-show_entries"); cmd.add("format=duration");
    cmd.add(inputFile.toAbsolutePath().toString());

    Process proc = new ProcessBuilder(cmd)
        .redirectErrorStream(true)
        .start();
    String output = new String(proc.getInputStream().readAllBytes()).trim();
    int exit = proc.waitFor();
    if (exit != 0) {
      throw new IllegalStateException("ffprobe exited with " + exit + ": " + output);
    }
    // output is a decimal like "183.421678"
    double durationSecs = Double.parseDouble(output);
    return (int) Math.round(durationSecs);
  }

  private void runFfmpegM4a(Path inputFile, Path outputFile, Integer durationLimit)
      throws IOException, InterruptedException {
    List<String> cmd = new ArrayList<>();
    cmd.add("ffmpeg");
    cmd.add("-y");
    cmd.add("-i"); cmd.add(inputFile.toAbsolutePath().toString());
    if (durationLimit != null) {
      cmd.add("-t"); cmd.add(String.valueOf(durationLimit));
    }
    cmd.add("-vn");                              // audio only — drop any cover-art video stream
    cmd.add("-c:a"); cmd.add("aac");
    cmd.add("-b:a"); cmd.add("128k");
    cmd.add("-movflags"); cmd.add("+faststart"); // moov atom first: playable before fully downloaded
    cmd.add(outputFile.toAbsolutePath().toString());

    Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    String output = new String(proc.getInputStream().readAllBytes()).trim();
    int exit = proc.waitFor();
    if (exit != 0) {
      throw new IllegalStateException("ffmpeg exited with " + exit + ": " + output);
    }
  }

  private String deliveryBucketOf(ObjectKey original) {
    // S3: use the injected delivery bucket name — never derive from originals bucket string. S3.
    return bucketDelivery;
  }

  private void deleteSilently(Path path) {
    if (path != null) {
      try {
        Files.deleteIfExists(path);
      } catch (IOException ignored) {
        // best-effort cleanup
      }
    }
  }
}
