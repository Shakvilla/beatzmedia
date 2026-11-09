package org.shakvilla.beatzmedia.discovery.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.discovery.application.port.out.CollectionSnapshotRepository;
import org.shakvilla.beatzmedia.discovery.application.port.out.CollectionSnapshotRepository.Entry;
import org.shakvilla.beatzmedia.discovery.application.port.out.CollectionSnapshotRepository.Snapshot;
import org.shakvilla.beatzmedia.discovery.application.port.out.CollectionSnapshotRepository.SnapshotVersion;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Tag("integration")
class CollectionSnapshotRepositoryIT {

  @Inject CollectionSnapshotRepository repository;

  @Test
  @TestTransaction
  void activatesCompleteVersionsAndKeepsLastGoodSnapshotOnInvalidInput() {
    String slug = "top-" + System.nanoTime();
    String oldId = "old-" + slug;
    String newId = "new-" + slug;
    repository.publish(snapshot(oldId, slug, "single:one"));
    assertEquals(oldId, repository.active("GH", slug).orElseThrow().id());
    assertEquals("single", repository.entries(oldId, 0, 10).getFirst().contentKind());

    assertThrows(IllegalArgumentException.class, () -> repository.publish(new Snapshot(
        new SnapshotVersion(newId, "GH", slug, "collections-v1", Instant.now()),
        List.of(new Entry(2, "single", "bad", "Bad", null, null,
            "/track/bad", 1, "recent-acceleration")))));
    assertEquals(oldId, repository.active("GH", slug).orElseThrow().id());

    repository.publish(snapshot(newId, slug, "album:two"));
    assertEquals(newId, repository.active("GH", slug).orElseThrow().id());
    assertEquals("album", repository.entries(newId, 0, 10).getFirst().contentKind());
    assertTrue(repository.entries(oldId, 0, 10).size() == 1);
    assertThrows(IllegalArgumentException.class, () -> repository.publish(new Snapshot(
        new SnapshotVersion("stale-" + slug, "GH", slug, "collections-v1",
            Instant.parse("2020-01-01T00:00:00Z")),
        List.of(new Entry(1, "single", "stale", "Stale", null, null,
            "/track/stale", 1, "sustained-popularity")))));
    assertEquals(newId, repository.active("GH", slug).orElseThrow().id());
  }

  private static Snapshot snapshot(String versionId, String slug, String identity) {
    String[] parts = identity.split(":", 2);
    return new Snapshot(
        new SnapshotVersion(versionId, "GH", slug, "collections-v1", Instant.now()),
        List.of(new Entry(1, parts[0], parts[1], "Title", "Creator", null,
            "/" + parts[0] + "/" + parts[1], 100, "sustained-popularity")));
  }
}
