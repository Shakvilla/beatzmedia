package org.shakvilla.beatzmedia.platform.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.shakvilla.beatzmedia.platform.domain.DurableEventEnvelope;
import org.shakvilla.beatzmedia.platform.domain.DurableEventFilter;
import org.shakvilla.beatzmedia.platform.domain.DurableEventRecord;
import org.shakvilla.beatzmedia.platform.domain.DurableQueueHealth;
import org.shakvilla.beatzmedia.platform.domain.OutboxQueueSnapshot;
import org.shakvilla.beatzmedia.platform.domain.Page;

/** Persistence operations used by the short-transaction dispatcher. */
public interface DurableEventStore {

  List<DurableEventEnvelope> claim(int batchSize, Instant now, Duration lease);

  boolean wasHandled(String eventId, String consumerName);

  void markHandled(String eventId, String consumerName, Instant processedAt);

  void markCompleted(String eventId, Instant completedAt);

  void releaseForRetry(
      String eventId, Instant availableAt, String error, boolean terminalFailure);

  OutboxQueueSnapshot snapshot(Instant now);

  Page<DurableEventRecord> search(DurableEventFilter filter);

  DurableEventRecord find(String eventId);

  DurableEventRecord replay(String eventId, String actorId, Instant replayedAt);

  DurableQueueHealth health(Instant now);
}
