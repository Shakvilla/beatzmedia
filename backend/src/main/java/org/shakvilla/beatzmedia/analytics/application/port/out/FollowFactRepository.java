package org.shakvilla.beatzmedia.analytics.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.analytics.domain.FollowFact;

/**
 * Output port: append-only staging store for {@link FollowFact} rows, owned exclusively by
 * analytics. Populated by the {@code Followed} (kind=artist) event observer; consumed and marked
 * processed by the audience {@code RollupJob}. Analytics ADD §4.1 ({@code FollowEventSource}).
 */
public interface FollowFactRepository {

  void append(FollowFact fact);

  List<FollowFact> findUnprocessed();

  void markProcessed(List<String> factIds);
}
