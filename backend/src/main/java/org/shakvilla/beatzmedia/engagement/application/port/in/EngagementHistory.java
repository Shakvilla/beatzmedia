package org.shakvilla.beatzmedia.engagement.application.port.in;

import java.time.Instant;
import java.util.List;

import org.shakvilla.beatzmedia.engagement.domain.ContentKind;
import org.shakvilla.beatzmedia.engagement.domain.EngagementEventType;

public interface EngagementHistory {

  SessionStart startSession(String accountId);

  void checkpoint(String accountId, String sessionId, long sequence, CheckpointInput input);

  HistoryPage recentlyPlayed(String accountId, String cursor, int limit);

  HistoryPage continueListening(String accountId, String cursor, int limit);

  Preference preference(String accountId);

  Preference updatePreference(String accountId, boolean enabled);

  void clearHistory(String accountId);

  record SessionStart(boolean recording, String sessionId, Instant startedAt) {}

  record CheckpointInput(
      ContentKind contentKind,
      String contentId,
      EngagementEventType eventType,
      int positionSeconds,
      int durationSeconds,
      int listenedDeltaSeconds,
      String recommendationId) {}

  record Preference(boolean historyEnabled) {}

  record HistoryItem(
      String contentKind,
      String contentId,
      String title,
      String subtitle,
      String image,
      int positionSeconds,
      int durationSeconds,
      boolean completed,
      Instant lastActivityAt) {}

  record HistoryPage(List<HistoryItem> items, String nextCursor) {}
}
