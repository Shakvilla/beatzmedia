package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.adapter.in.events.MediaReadyObserver;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.MediaReady;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;

/**
 * MediaReady had no observers at all, so an upload that transcoded perfectly left its track on the
 * "uploading" stub forever — invisible to fans, with a 0:00 duration. These tests pin the
 * projection that publishes it.
 */
class MediaReadyObserverTest {

  private FakeCatalogRepository repo;
  private MediaReadyObserver observer;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    observer = new MediaReadyObserver(repo);
  }

  /** The stub row catalog writes at upload time: not ready, and duration not yet probed. */
  private Track uploadingStub(String id) {
    return new Track(
        new TrackId(id), "Sunset Groove", new ArtistId("art-1"), null, null, null,
        0, "/images/placeholder.jpg", OwnershipStatus.free, null, 0L, null, null, null, null,
        "uploading");
  }

  private static MediaReady audioReady(String trackId, int durationSec) {
    return new MediaReady(
        new MediaAssetId("asset-1"), new OwnerRef("catalog", trackId), MediaKind.AUDIO,
        durationSec);
  }

  @Test
  void publishes_the_track_and_records_the_probed_duration() {
    repo.saveTrack(uploadingStub("t1"));

    observer.onMediaReady(audioReady("t1", 214));

    Track saved = repo.findTrack(new TrackId("t1")).orElseThrow();
    assertEquals("ready", saved.getStatus(), "track must become visible to fans");
    // The stub carried 0 — the real duration is only known once ffprobe has run in transcode.
    assertEquals(214, saved.getDurationSec());
  }

  @Test
  void ignores_artwork_because_it_does_not_make_a_track_playable() {
    repo.saveTrack(uploadingStub("t2"));

    observer.onMediaReady(
        new MediaReady(
            new MediaAssetId("asset-2"), new OwnerRef("catalog", "t2"), MediaKind.ARTWORK, 0));

    assertEquals("uploading", repo.findTrack(new TrackId("t2")).orElseThrow().getStatus());
  }

  @Test
  void ignores_audio_owned_by_another_module() {
    // Studio episodes ride the same event; catalog must not claim them.
    repo.saveTrack(uploadingStub("t3"));

    observer.onMediaReady(
        new MediaReady(
            new MediaAssetId("asset-3"), new OwnerRef("studio", "t3"), MediaKind.AUDIO, 90));

    assertEquals("uploading", repo.findTrack(new TrackId("t3")).orElseThrow().getStatus());
  }

  @Test
  void a_missing_track_does_not_blow_up_the_observer() {
    // A draft track deleted while its audio was still transcoding. The asset outlives the track;
    // that is survivable, and an exception here would poison the AFTER_SUCCESS callback.
    observer.onMediaReady(audioReady("nope", 100));

    assertEquals(List.of(), repo.findTrack(new TrackId("nope")).map(List::of).orElse(List.of()));
  }

  @Test
  void replaying_the_same_event_writes_nothing_new() {
    repo.saveTrack(uploadingStub("t4"));
    observer.onMediaReady(audioReady("t4", 180));
    Track afterFirst = repo.findTrack(new TrackId("t4")).orElseThrow();

    // A re-transcode re-fires MediaReady. Idempotent: same state, and markReady returns the very
    // same instance so no pointless write reaches the repository.
    observer.onMediaReady(audioReady("t4", 180));

    assertSame(afterFirst, repo.findTrack(new TrackId("t4")).orElseThrow());
    assertEquals("ready", afterFirst.getStatus());
  }
}
