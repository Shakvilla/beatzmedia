package org.shakvilla.beatzmedia.playback.fakes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.playback.application.port.out.MediaService;
import org.shakvilla.beatzmedia.playback.domain.PlaybackMode;

/**
 * In-memory fake for {@link MediaService} used in unit tests. Records every call so tests can
 * assert the server-side rendition decision reached the media call boundary correctly (INV-3).
 */
public class FakeMediaService implements MediaService {

  public record Call(TrackId track, PlaybackMode mode, Duration ttl) {}

  private final List<Call> calls = new ArrayList<>();
  private Instant expiresAt = Instant.parse("2026-06-22T12:05:00Z");

  public FakeMediaService expiresAt(Instant instant) {
    this.expiresAt = instant;
    return this;
  }

  @Override
  public SignedUrl issueSignedUrl(TrackId track, PlaybackMode mode, Duration ttl) {
    calls.add(new Call(track, mode, ttl));
    // Mirrors the real delivery key shape (FfmpegAudioTranscoderAdapter): delivery/{id}/full.m4a
    // and delivery/{id}/preview.m4a. Tests assert on these two filenames, which discriminate — a
    // stale "hls" fixture made the FULL assertion unsatisfiable and the PREVIEW one vacuous.
    String url =
        mode == PlaybackMode.FULL
            ? "https://cdn.test/delivery/" + track.value() + "/full.m4a?sig=full"
            : "https://cdn.test/delivery/" + track.value() + "/preview.m4a?sig=preview";
    return new SignedUrl(url, expiresAt);
  }

  public List<Call> calls() {
    return calls;
  }

  public Call lastCall() {
    return calls.get(calls.size() - 1);
  }
}
