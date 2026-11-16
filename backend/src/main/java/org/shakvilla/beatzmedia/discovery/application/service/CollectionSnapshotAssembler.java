package org.shakvilla.beatzmedia.discovery.application.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.in.GetDiscoveryPurchaseMetrics;
import org.shakvilla.beatzmedia.discovery.application.port.out.CollectionContent.Group;
import org.shakvilla.beatzmedia.discovery.application.port.out.CollectionSnapshotRepository.Entry;
import org.shakvilla.beatzmedia.discovery.application.port.out.CollectionSnapshotRepository.Snapshot;
import org.shakvilla.beatzmedia.discovery.application.port.out.CollectionSnapshotRepository.SnapshotVersion;
import org.shakvilla.beatzmedia.discovery.domain.CollectionRanker;
import org.shakvilla.beatzmedia.discovery.domain.CollectionRanker.Chart;
import org.shakvilla.beatzmedia.discovery.domain.CollectionRanker.Metrics;
import org.shakvilla.beatzmedia.engagement.application.port.in.GetDiscoveryEngagementMetrics;
import org.shakvilla.beatzmedia.library.application.port.in.GetDiscoveryLibraryMetrics;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/** Assembles an organic snapshot after checking each content group's explicit country. */
@ApplicationScoped
public class CollectionSnapshotAssembler {

  private static final int MAX_CANDIDATES = 100;
  private static final int MAX_ENTRIES = 50;

  private final GetDiscoveryEngagementMetrics engagement;
  private final GetDiscoveryLibraryMetrics library;
  private final GetDiscoveryPurchaseMetrics purchases;
  private final IdGenerator ids;

  @Inject
  public CollectionSnapshotAssembler(
      GetDiscoveryEngagementMetrics engagement,
      GetDiscoveryLibraryMetrics library,
      GetDiscoveryPurchaseMetrics purchases,
      IdGenerator ids) {
    this.engagement = engagement;
    this.library = library;
    this.purchases = purchases;
    this.ids = ids;
  }

  /**
   * The caller supplies currently published groups. This method filters their explicit country
   * before ranking. No snapshot is published here: the scheduled job will recheck publication
   * state and perform atomic publication.
   * Emerging requires historical reach metrics and is deliberately unavailable until they exist.
   */
  public Optional<Snapshot> assemble(
      Chart chart, String market, List<Group> candidates, Instant asOf) {
    if (chart == null || chart == Chart.EMERGING || market == null
        || !market.matches("[A-Z]{2}") || candidates == null
        || candidates.size() > MAX_CANDIDATES || asOf == null) {
      throw new IllegalArgumentException("Invalid or unsupported collection snapshot request");
    }
    Instant since = asOf.minus(28, ChronoUnit.DAYS);
    Map<String, Group> groups = new LinkedHashMap<>();
    for (Group group : candidates) {
      if (eligible(group, market, asOf)) groups.putIfAbsent(group.key(), group);
    }
    List<Metrics> metrics = new ArrayList<>();
    for (Group group : groups.values()) {
      var listening = engagement.group(group.members(), asOf);
      var social = library.get(group.kind(), group.id(), group.creatorId(), since, asOf);
      long conversions = purchases.count(group.kind(), group.id(),
          group.members().stream().map(member -> member.id()).toList(), since, asOf);
      long ageDays = ChronoUnit.DAYS.between(group.publishedAt(), asOf);
      metrics.add(new Metrics(
          group.key(), listening.qualifiedPlays28d(), listening.qualifiedPlays24h(),
          listening.qualifiedPlaysPrevious7d(), listening.uniqueListeners28d(),
          listening.uniqueListeners24h(), listening.completions28d(), social.saves(),
          // Artist follows belong to the creator, not to each release. Show follows identify a show.
          "podcast".equals(group.kind()) ? social.creatorFollows() : 0,
          conversions, 0, 0, (int) Math.min(Integer.MAX_VALUE, ageDays)));
    }
    List<CollectionRanker.Ranked> ranked = CollectionRanker.rank(
        chart, metrics, CollectionRanker.Policy.initial()).stream().limit(MAX_ENTRIES).toList();
    if (ranked.isEmpty()) return Optional.empty();
    List<Entry> entries = new ArrayList<>();
    for (CollectionRanker.Ranked item : ranked) {
      Group group = groups.get(item.contentKey());
      entries.add(new Entry(entries.size() + 1, group.kind(), group.id(), group.title(),
          group.subtitle(), group.image(), group.route(), item.score(), item.reasonCode()));
    }
    SnapshotVersion version = new SnapshotVersion(
        ids.newId(), market, chart.name().toLowerCase(java.util.Locale.ROOT),
        CollectionRanker.FORMULA_VERSION, asOf);
    return Optional.of(new Snapshot(version, List.copyOf(entries)));
  }

  private static boolean eligible(Group group, String market, Instant asOf) {
    return group != null && ("single".equals(group.kind()) || "album".equals(group.kind())
        || "ep".equals(group.kind()) || "podcast".equals(group.kind()))
        && market.equals(group.originCountry())
        && group.id() != null && !group.id().isBlank()
        && group.creatorId() != null && !group.creatorId().isBlank()
        && group.title() != null && !group.title().isBlank()
        && group.route() != null && !group.route().isBlank()
        && group.publishedAt() != null && !group.publishedAt().isAfter(asOf)
        && group.members() != null && !group.members().isEmpty()
        && group.members().size() <= 100;
  }
}
