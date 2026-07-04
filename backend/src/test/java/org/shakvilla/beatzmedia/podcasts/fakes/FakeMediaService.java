package org.shakvilla.beatzmedia.podcasts.fakes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.podcasts.application.port.out.MediaService;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;

/**
 * In-memory fake for {@link MediaService} used in unit tests. Records every call so tests can
 * assert the server-side rendition decision reached the media call boundary correctly (INV-3).
 */
public class FakeMediaService implements MediaService {

  public record Call(EpisodeId episode, boolean preview, Duration ttl) {}

  private final List<Call> calls = new ArrayList<>();
  private Instant expiresAt = Instant.parse("2026-06-22T12:05:00Z");

  public FakeMediaService expiresAt(Instant instant) {
    this.expiresAt = instant;
    return this;
  }

  @Override
  public SignedUrl issueSignedUrl(EpisodeId episode, boolean preview, Duration ttl) {
    calls.add(new Call(episode, preview, ttl));
    String url =
        preview
            ? "https://cdn.test/delivery/" + episode.value() + "/preview/preview.m3u8?sig=preview"
            : "https://cdn.test/delivery/" + episode.value() + "/hls/playlist.m3u8?sig=full";
    return new SignedUrl(url, expiresAt);
  }

  public List<Call> calls() {
    return calls;
  }

  public Call lastCall() {
    return calls.get(calls.size() - 1);
  }
}
