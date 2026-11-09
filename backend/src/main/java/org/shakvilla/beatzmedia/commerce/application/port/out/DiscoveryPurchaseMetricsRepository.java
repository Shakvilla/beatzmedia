package org.shakvilla.beatzmedia.commerce.application.port.out;

import java.time.Instant;
import java.util.List;

public interface DiscoveryPurchaseMetricsRepository {

  long count(String contentKind, String contentId, List<String> memberIds, Instant since, Instant asOf);
}
