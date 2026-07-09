package org.shakvilla.beatzmedia.studio.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.port.in.UploadOriginalUseCase;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.MediaStatus;
import org.shakvilla.beatzmedia.media.domain.UnsupportedFormatException;

/**
 * In-memory fake for {@link UploadOriginalUseCase} used in studio unit tests. Records every call so
 * tests can assert the media pipeline was (or was not) invoked — the key assertion for the
 * idempotency-replay test (no second upload).
 */
public class FakeUploadOriginalUseCase implements UploadOriginalUseCase {

  private final AtomicInteger callCount = new AtomicInteger();
  private final List<UploadCommand> commands = new ArrayList<>();
  private int fixedDurationSec = 120;
  private boolean rejectNextUpload = false;

  @Override
  public MediaHandle uploadOriginal(UploadCommand command) {
    callCount.incrementAndGet();
    commands.add(command);
    if (rejectNextUpload) {
      rejectNextUpload = false;
      throw new UnsupportedFormatException("fake rejection");
    }
    return new MediaHandle(
        new MediaAssetId("asset-" + callCount.get()), command.kind(), fixedDurationSec,
        MediaStatus.UPLOADING);
  }

  public int callCount() {
    return callCount.get();
  }

  public List<UploadCommand> commands() {
    return List.copyOf(commands);
  }

  public FakeUploadOriginalUseCase withDurationSec(int durationSec) {
    this.fixedDurationSec = durationSec;
    return this;
  }

  public void rejectNextUpload() {
    this.rejectNextUpload = true;
  }
}
