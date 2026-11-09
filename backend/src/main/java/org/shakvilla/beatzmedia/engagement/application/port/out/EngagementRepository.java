package org.shakvilla.beatzmedia.engagement.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.engagement.application.port.in.GetDiscoveryEngagementMetrics;
import org.shakvilla.beatzmedia.engagement.domain.ContentKind;
import org.shakvilla.beatzmedia.engagement.domain.EngagementEventType;
import org.shakvilla.beatzmedia.engagement.domain.ListeningCheckpoint;

public interface EngagementRepository {

  PreferenceRow lockPreference(String accountId, Instant now);

  PreferenceRow preference(String accountId);

  void savePreference(String accountId, boolean enabled, Instant now);

  void createSession(String id, String accountId, Instant startedAt);

  Optional<SessionRow> lockSession(String id, String accountId);

  Optional<ListeningCheckpoint> checkpoint(String sessionId, long sequence);

  void insertCheckpoint(ListeningCheckpoint checkpoint);

  void advanceSessionAndProgress(ListeningCheckpoint checkpoint);

  List<ProgressRow> progress(
      String accountId, ContentKind onlyKind, boolean incompleteOnly, Cursor cursor, int limit);

  void deletePersonalHistory(String accountId, Instant clearedAt);

  int projectPending(int batchSize, Instant projectedAt);

  List<GetDiscoveryEngagementMetrics.ContentMetrics> discoveryMetrics(
      List<GetDiscoveryEngagementMetrics.ContentRef> content, Instant asOf);

  List<GetDiscoveryEngagementMetrics.ContentRef> discoveryCandidates(
      Instant asOf, int perWindowLimit);

  GetDiscoveryEngagementMetrics.GroupMetrics discoveryGroupMetrics(
      List<GetDiscoveryEngagementMetrics.ContentRef> members, Instant asOf);

  record PreferenceRow(boolean historyEnabled) {}

  record SessionRow(String id, String accountId, long maxSequence) {}

  record ProgressRow(
      String accountId,
      ContentKind contentKind,
      String contentId,
      int positionSeconds,
      int durationSeconds,
      EngagementEventType lastEventType,
      boolean completed,
      Instant lastActivityAt) {}

  record Cursor(Instant lastActivityAt, ContentKind contentKind, String contentId) {}
}
