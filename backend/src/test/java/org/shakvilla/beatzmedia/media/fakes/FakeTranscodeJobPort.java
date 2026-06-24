package org.shakvilla.beatzmedia.media.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.media.application.port.out.TranscodeJob;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeJobPort;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeResult;

/** In-memory fake for {@link TranscodeJobPort}. Captures submitted jobs for assertion in tests. */
public class FakeTranscodeJobPort implements TranscodeJobPort {

  private final List<TranscodeJob> submitted = new ArrayList<>();
  private final List<TranscodeResult> results = new ArrayList<>();

  @Override
  public void submit(TranscodeJob job) {
    submitted.add(job);
  }

  @Override
  public void onResult(TranscodeResult result) {
    results.add(result);
  }

  public List<TranscodeJob> getSubmitted() {
    return submitted;
  }

  public List<TranscodeResult> getResults() {
    return results;
  }
}
