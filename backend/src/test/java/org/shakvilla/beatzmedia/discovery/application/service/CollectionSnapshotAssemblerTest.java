package org.shakvilla.beatzmedia.discovery.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.in.GetDiscoveryPurchaseMetrics;
import org.shakvilla.beatzmedia.discovery.application.port.out.CollectionContent.Group;
import org.shakvilla.beatzmedia.discovery.domain.CollectionRanker.Chart;
import org.shakvilla.beatzmedia.engagement.application.port.in.GetDiscoveryEngagementMetrics;
import org.shakvilla.beatzmedia.engagement.application.port.in.GetDiscoveryEngagementMetrics.ContentMetrics;
import org.shakvilla.beatzmedia.engagement.application.port.in.GetDiscoveryEngagementMetrics.ContentRef;
import org.shakvilla.beatzmedia.engagement.application.port.in.GetDiscoveryEngagementMetrics.GroupMetrics;
import org.shakvilla.beatzmedia.library.application.port.in.GetDiscoveryLibraryMetrics;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

@Tag("unit")
class CollectionSnapshotAssemblerTest {

  private static final Instant AS_OF = Instant.parse("2026-11-06T12:00:00Z");

  @Test
  void ranksGroupedReleasesAndShowsUsingSettledPurchasesWithoutRepeatingArtistFollows() {
    List<List<String>> purchasedMembers = new ArrayList<>();
    GetDiscoveryPurchaseMetrics purchases = (kind, id, members, since, asOf) -> {
      purchasedMembers.add(members);
      return 1;
    };
    GetDiscoveryLibraryMetrics library = (kind, id, creator, since, asOf) ->
        "podcast".equals(kind) ? new GetDiscoveryLibraryMetrics.Signals(0, 3)
            : new GetDiscoveryLibraryMetrics.Signals(2, 99);
    var assembler = new CollectionSnapshotAssembler(
        new FakeEngagement(), library, purchases, FakeIds.sequential("snapshot"));
    Group album = group("album", "album-1", "artist-1", List.of(
        new ContentRef("track", "track-1"), new ContentRef("track", "track-2")));
    Group show = group("podcast", "show-1", "show-1", List.of(
        new ContentRef("podcast-episode", "episode-1")));

    var snapshot = assembler.assemble(Chart.TOP, "GH", List.of(album, show), AS_OF).orElseThrow();

    assertEquals("GH", snapshot.version().market());
    assertEquals("top", snapshot.version().slug());
    assertEquals(List.of("podcast", "album"),
        snapshot.entries().stream().map(entry -> entry.contentKind()).toList());
    assertEquals(List.of(1, 2),
        snapshot.entries().stream().map(entry -> entry.position()).toList());
    assertEquals(List.of(List.of("track-1", "track-2"), List.of("episode-1")), purchasedMembers);
  }

  @Test
  void leavesActiveSnapshotUntouchedWhenNoEligibleCardPassesTheThreshold() {
    var assembler = new CollectionSnapshotAssembler(new FakeEngagement(),
        (kind, id, creator, since, asOf) -> new GetDiscoveryLibraryMetrics.Signals(0, 0),
        (kind, id, members, since, asOf) -> 0, FakeIds.sequential("snapshot"));
    Group future = new Group("album", "future", "artist", "Future", "Artist", null,
        "/album/future", AS_OF.plusSeconds(1), List.of(), List.of(new ContentRef("track", "t")),
        "GH");

    assertTrue(assembler.assemble(Chart.TOP, "GH", List.of(future), AS_OF).isEmpty());
    Group unknown = new Group("album", "unknown", "artist", "Unknown", "Artist", null,
        "/album/unknown", AS_OF.minusSeconds(1), List.of(),
        List.of(new ContentRef("track", "t")));
    Group foreign = new Group("album", "foreign", "artist", "Foreign", "Artist", null,
        "/album/foreign", AS_OF.minusSeconds(1), List.of(),
        List.of(new ContentRef("track", "t")), "NG");
    assertTrue(assembler.assemble(Chart.TOP, "GH", List.of(unknown, foreign), AS_OF).isEmpty());
    assertThrows(IllegalArgumentException.class,
        () -> assembler.assemble(Chart.EMERGING, "GH", List.of(future), AS_OF));
  }

  private static Group group(String kind, String id, String creator, List<ContentRef> members) {
    return new Group(kind, id, creator, id, creator, null, "/" + kind + "/" + id,
        AS_OF.minusSeconds(86400), List.of(), members, "GH");
  }

  private static final class FakeEngagement implements GetDiscoveryEngagementMetrics {
    @Override
    public List<ContentMetrics> get(List<ContentRef> content, Instant asOf) {
      return List.of();
    }

    @Override
    public List<ContentRef> candidates(Instant asOf, int perWindowLimit) {
      return List.of();
    }

    @Override
    public GroupMetrics group(List<ContentRef> members, Instant asOf) {
      if ("track".equals(members.getFirst().kind())) {
        return new GroupMetrics(10, 4, 3, 6, 4, 5);
      }
      return new GroupMetrics(12, 5, 4, 7, 5, 5);
    }
  }
}
