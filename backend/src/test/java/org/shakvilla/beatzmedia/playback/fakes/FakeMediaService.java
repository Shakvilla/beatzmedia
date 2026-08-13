package org.shakvilla.beatzmedia.playback.fakes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

  // ---- LOSSLESS (download) rendition ----

  /** A request for the lossless download rendition; ttl recorded so the TTL wiring is assertable. */
  public record LosslessCall(TrackId track, Duration ttl) {}

  private final Set<String> lossless = new HashSet<>();
  private final List<LosslessCall> losslessCalls = new ArrayList<>();

  /** Seed "the FLAC rendition for this track exists and is signable". */
  public FakeMediaService hasLossless(String trackId) {
    lossless.add(trackId);
    return this;
  }

  @Override
  public Optional<SignedUrl> issueLosslessUrl(TrackId track, Duration ttl) {
    losslessCalls.add(new LosslessCall(track, ttl));
    if (!lossless.contains(track.value())) {
      // Mirrors the real adapter: no asset, or MediaAsset.resolveDeliveryKey(LOSSLESS) throwing
      // because losslessKey is null. Deliberately NOT a fall back to the AAC full rendition.
      return Optional.empty();
    }
    // Mirrors the real delivery key shape (FfmpegAudioTranscoderAdapter):
    // delivery/{id}/lossless.flac — discriminable from delivery/{id}/full.m4a, so a test can
    // prove the download never hands back the lossy stream rendition.
    return Optional.of(
        new SignedUrl(
            "https://cdn.test/delivery/" + track.value() + "/lossless.flac?sig=lossless",
            expiresAt));
  }

  public List<LosslessCall> losslessCalls() {
    return losslessCalls;
  }
}
