package org.shakvilla.beatzmedia.notifications.application.port.out;

import java.time.Instant;
import java.util.List;

import org.shakvilla.beatzmedia.notifications.domain.PublicationFanoutJob;

/** Persistence boundary for durable, cursor-resumable publication notification fanout. */
public interface PublicationFanoutRepository {

  /** Insert once by publication natural key; replay never resets existing progress. */
  void enqueue(PublicationFanoutJob job);

  List<PublicationFanoutJob> findDue(Instant now, int limit);

  void advance(String jobId, String nextCursor, Instant now);

  void complete(String jobId, Instant now);

  void reschedule(String jobId, int retryCount, Instant nextAttemptAt, String error, Instant now);
}
