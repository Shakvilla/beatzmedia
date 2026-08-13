package org.shakvilla.beatzmedia.media.fakes;

import org.shakvilla.beatzmedia.media.application.port.out.AudioTranscoderPort;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

/**
 * In-memory fake for {@link AudioTranscoderPort}. Every stage succeeds by default; call
 * {@link #failLosslessOnly()} to make only {@link #transcodeLossless} throw, for exercising the
 * Task 12 failure-containment behaviour (a lost FLAC must not fail the asset).
 */
public class FakeAudioTranscoderPort implements AudioTranscoderPort {

  private boolean failLossless = false;

  public void failLosslessOnly() {
    this.failLossless = true;
  }

  @Override
  public int probeDurationSec(ObjectKey original) {
    return 183;
  }

  @Override
  public ObjectKey transcodeFull(ObjectKey original, MediaAssetId id) {
    return new ObjectKey("delivery", "delivery/" + id.value() + "/full.m4a");
  }

  @Override
  public ObjectKey clipPreview(ObjectKey original, MediaAssetId id, int previewSeconds) {
    return new ObjectKey("delivery", "delivery/" + id.value() + "/preview.m4a");
  }

  @Override
  public ObjectKey transcodeLossless(ObjectKey original, MediaAssetId id) {
    if (failLossless) {
      throw new RuntimeException("simulated lossless transcode failure");
    }
    return new ObjectKey("delivery", "delivery/" + id.value() + "/lossless.flac");
  }
}
