package org.shakvilla.beatzmedia.library.application.port.in;

import java.time.Instant;

/** Aggregate social signals for published discovery cards; contains no fan identities. */
public interface GetDiscoveryLibraryMetrics {

  Signals get(String contentKind, String contentId, String creatorId, Instant since, Instant asOf);

  record Signals(long saves, long creatorFollows) {}
}
