package org.shakvilla.beatzmedia.engagement.application.port.in;

import java.time.Instant;
import java.util.List;

/** Bounded aggregate read for content discovery; exposes no account identities. */
public interface GetDiscoveryEngagementMetrics {

  List<ContentMetrics> get(List<ContentRef> content, Instant asOf);

  /** Union of the strongest 28-day and 24-hour content windows, before chart scoring. */
  List<ContentRef> candidates(Instant asOf, int perWindowLimit);

  /** Metrics across one release or show, deduplicating the same listener across members. */
  GroupMetrics group(List<ContentRef> members, Instant asOf);

  record ContentRef(String kind, String id) {}

  record ContentMetrics(
      String kind,
      String id,
      long qualifiedPlays28d,
      long qualifiedPlays24h,
      long qualifiedPlaysPrevious7d,
      long uniqueListeners28d,
      long uniqueListeners24h,
      long completions28d) {}

  record GroupMetrics(
      long qualifiedPlays28d,
      long qualifiedPlays24h,
      long qualifiedPlaysPrevious7d,
      long uniqueListeners28d,
      long uniqueListeners24h,
      long completions28d) {}
}
