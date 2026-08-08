package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.adapter.out.search.ReleaseSearchProjectionObserver;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ContentTakenDown;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseWentLive;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.search.application.port.in.IndexEntityUseCase;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;

/**
 * Approving a release used to leave its tracks {@code visible = false} in {@code search_document}
 * until the next periodic backfill, so a just-published track was unsearchable; a takedown left the
 * mirror problem, a pulled track that stayed searchable. These pin both directions.
 */
class ReleaseSearchProjectionObserverTest {

  private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
  private static final String ARTIST = "artist-1";

  private FakeCatalogRepository repo;
  private RecordingIndex index;
  private ReleaseSearchProjectionObserver observer;

  /** Captures what was written to the index, in order. Fakes over mocks, per the project. */
  static class RecordingIndex implements IndexEntityUseCase {
    final List<IndexDocument> indexed = new ArrayList<>();
    final List<String> deindexed = new ArrayList<>();

    @Override
    public void index(IndexDocument document) {
      indexed.add(document);
    }

    @Override
    public void deindex(EntityType type, String entityId) {
      deindexed.add(entityId);
    }
  }

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    index = new RecordingIndex();
    observer = new ReleaseSearchProjectionObserver(repo, index);
  }

  private Release liveRelease(String id, String... trackIds) {
    List<ReleaseTrack> tracks = new ArrayList<>();
    for (int i = 0; i < trackIds.length; i++) {
      tracks.add(new ReleaseTrack(trackIds[i], i + 1, 500));
    }
    Release r = Release.create(
        id, ARTIST, "Test Release", ReleaseType.single, Visibility.PUBLIC, null, tracks, 24, NOW);
    r.approveImmediate(NOW);
    return r;
  }

  private void seedTrack(String id) {
    repo.saveTrack(new Track(
        new TrackId(id), "Track " + id, new ArtistId(ARTIST), null, null, null,
        200, "/images/placeholder.jpg", OwnershipStatus.free, null, 0L, null, null, null, null,
        "ready"));
  }

  @Test
  void going_live_indexes_every_track_of_the_release_as_visible() {
    seedTrack("t1");
    seedTrack("t2");
    repo.addRelease(liveRelease("rel-1", "t1", "t2"));

    observer.onReleaseWentLive(new ReleaseWentLive("rel-1", ARTIST, NOW));

    assertEquals(2, index.indexed.size(), "both tracks must be reprojected");
    assertTrue(
        index.indexed.stream().allMatch(IndexDocument::visible),
        "a track on a live release must be searchable");
    assertEquals(
        List.of("t1", "t2"),
        index.indexed.stream().map(IndexDocument::entityId).sorted().toList());
  }

  @Test
  void takedown_reprojects_the_tracks_as_hidden() {
    seedTrack("t1");
    repo.addRelease(liveRelease("rel-2", "t1"));

    observer.onContentTakenDown(
        new ContentTakenDown("rel-2", ARTIST, "admin-1", "DMCA claim", NOW));

    assertEquals(1, index.indexed.size());
    assertFalse(
        index.indexed.get(0).visible(),
        "a taken-down track must stop being searchable, not linger as visible");
    // Hidden by upsert, not deleted: reindex is upsert-only and the row must stay reconcilable.
    assertTrue(index.deindexed.isEmpty(), "takedown should soft-hide, not deindex");
  }

  @Test
  void a_release_deleted_before_the_observer_runs_does_not_blow_up() {
    // AFTER_SUCCESS runs outside the original transaction, so the release can be gone by then.
    // Throwing here would poison the callback for everything else observing the same event.
    observer.onReleaseWentLive(new ReleaseWentLive("rel-missing", ARTIST, NOW));

    assertTrue(index.indexed.isEmpty());
  }

  @Test
  void a_release_with_no_tracks_writes_nothing() {
    repo.addRelease(liveRelease("rel-empty"));

    observer.onReleaseWentLive(new ReleaseWentLive("rel-empty", ARTIST, NOW));

    assertTrue(index.indexed.isEmpty(), "no tracks means no documents, not an empty write");
  }
}
