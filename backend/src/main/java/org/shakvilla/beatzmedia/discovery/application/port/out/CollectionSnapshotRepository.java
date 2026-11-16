package org.shakvilla.beatzmedia.discovery.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CollectionSnapshotRepository {

  void publish(Snapshot snapshot);

  Optional<SnapshotVersion> active(String market, String slug);

  List<Entry> entries(String versionId, int afterPosition, int limit);

  record SnapshotVersion(
      String id, String market, String slug, String formulaVersion, Instant generatedAt) {}

  record Entry(
      int position,
      String contentKind,
      String contentId,
      String title,
      String subtitle,
      String image,
      String route,
      long score,
      String reasonCode) {}

  record Snapshot(SnapshotVersion version, List<Entry> entries) {}
}
