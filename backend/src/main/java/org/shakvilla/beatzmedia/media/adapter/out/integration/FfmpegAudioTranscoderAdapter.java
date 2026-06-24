package org.shakvilla.beatzmedia.media.adapter.out.integration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
 * transcodes to HLS + 30s preview, uploads segments back, cleans up temp files.
 * ADD §5.2 / ADR (WU-MED-1 §2).
 */
@ApplicationScoped
public class FfmpegAudioTranscoderAdapter implements AudioTranscoderPort {

  private static final String HLS_SEGMENT_DURATION = "6";

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
  public ObjectKey transcodeHls(ObjectKey original, MediaAssetId id) {
    Path tmpInput = null;
    Path tmpDir = null;
    try {
      tmpInput = downloadToTemp(original, "hls-", ".audio");
      tmpDir = Files.createTempDirectory("hls-out-" + id.value());
      Path playlistPath = tmpDir.resolve("playlist.m3u8");

      runFfmpegHls(tmpInput, tmpDir, playlistPath, null);

      String hlsKeyPrefix = "delivery/" + id.value() + "/hls/";
      uploadHlsDir(tmpDir, id, hlsKeyPrefix);

      return new ObjectKey(deliveryBucketOf(original), hlsKeyPrefix + "playlist.m3u8");
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ffmpeg HLS transcode failed for " + id.value(), e);
    } finally {
      deleteDirSilently(tmpDir);
      deleteSilently(tmpInput);
    }
  }

  @Override
  public ObjectKey clipPreviewHls(ObjectKey original, MediaAssetId id, int previewSeconds) {
    Path tmpInput = null;
    Path tmpDir = null;
    try {
      tmpInput = downloadToTemp(original, "preview-", ".audio");
      tmpDir = Files.createTempDirectory("preview-out-" + id.value());
      Path playlistPath = tmpDir.resolve("preview.m3u8");

      runFfmpegHls(tmpInput, tmpDir, playlistPath, previewSeconds);

      String previewKeyPrefix = "delivery/" + id.value() + "/preview/";
      uploadHlsDir(tmpDir, id, previewKeyPrefix);

      return new ObjectKey(deliveryBucketOf(original), previewKeyPrefix + "preview.m3u8");
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ffmpeg preview clip failed for " + id.value(), e);
    } finally {
      deleteDirSilently(tmpDir);
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

  private void runFfmpegHls(
      Path inputFile, Path outputDir, Path playlistFile, Integer durationLimit)
      throws IOException, InterruptedException {
    List<String> cmd = new ArrayList<>();
    cmd.add("ffmpeg");
    cmd.add("-y");
    cmd.add("-i"); cmd.add(inputFile.toAbsolutePath().toString());
    if (durationLimit != null) {
      cmd.add("-t"); cmd.add(String.valueOf(durationLimit));
    }
    cmd.add("-c:a"); cmd.add("aac");
    cmd.add("-b:a"); cmd.add("128k");
    cmd.add("-hls_time"); cmd.add(HLS_SEGMENT_DURATION);
    cmd.add("-hls_list_size"); cmd.add("0");
    cmd.add("-hls_segment_filename"); cmd.add(outputDir.toAbsolutePath() + "/segment%03d.ts");
    cmd.add("-hls_flags"); cmd.add("independent_segments");
    cmd.add(playlistFile.toAbsolutePath().toString());

    Process proc = new ProcessBuilder(cmd)
        .redirectErrorStream(true)
        .start();
    String output = new String(proc.getInputStream().readAllBytes()).trim();
    int exit = proc.waitFor();
    if (exit != 0) {
      throw new IllegalStateException("ffmpeg exited with " + exit + ": " + output);
    }
  }

  private void uploadHlsDir(Path dir, MediaAssetId id, String keyPrefix) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      for (Path file : (Iterable<Path>) files::iterator) {
        if (Files.isRegularFile(file)) {
          String filename = file.getFileName().toString();
          String relKey = keyPrefix + filename;
          String contentType = filename.endsWith(".m3u8") ? "application/x-mpegURL" : "video/mp2t";
          try (InputStream in = Files.newInputStream(file)) {
            objectStore.putDelivery(id, relKey, in, contentType);
          }
        }
      }
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

  private void deleteDirSilently(Path dir) {
    if (dir == null) return;
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.sorted(java.util.Comparator.reverseOrder())
          .forEach(p -> {
            try { Files.deleteIfExists(p); } catch (IOException ignored) { }
          });
    } catch (IOException ignored) {
      // best-effort cleanup
    }
  }
}
