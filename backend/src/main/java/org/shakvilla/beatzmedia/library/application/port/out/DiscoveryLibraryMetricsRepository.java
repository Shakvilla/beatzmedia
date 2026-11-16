package org.shakvilla.beatzmedia.library.application.port.out;

import java.time.Instant;

import org.shakvilla.beatzmedia.library.application.port.in.GetDiscoveryLibraryMetrics.Signals;

public interface DiscoveryLibraryMetricsRepository {

  Signals get(String contentKind, String contentId, String creatorId, Instant since, Instant asOf);
}
