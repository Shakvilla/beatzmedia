package org.shakvilla.beatzmedia.commerce.application.port.in;

import java.time.Instant;
import java.util.List;

/** Settled, non-refunded order counts for a published discovery card. */
public interface GetDiscoveryPurchaseMetrics {

  long count(String contentKind, String contentId, List<String> memberIds, Instant since, Instant asOf);
}
