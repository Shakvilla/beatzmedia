package org.shakvilla.beatzmedia.analytics.adapter.in.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.analytics.domain.TipFact;
import org.shakvilla.beatzmedia.analytics.fakes.InMemoryTipFactRepository;
import org.shakvilla.beatzmedia.payments.domain.TipReceived;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/** Unit test for {@link TipReceivedObserver} — stages one fact per observed event. */
@Tag("unit")
class TipReceivedObserverTest {

  @Test
  void onTipReceived_stagesOneFactWithTheCreatorShare() {
    InMemoryTipFactRepository facts = new InMemoryTipFactRepository();
    TipReceivedObserver observer = new TipReceivedObserver(facts, FakeIds.sequential("fact"));
    Instant at = Instant.parse("2026-07-05T10:00:00Z");

    observer.onTipReceived(new TipReceived("intent-1", "fan-1", "artist-9", 500L, 450L, 50L, "GHS", at));

    assertEquals(1, facts.findUnprocessed().size());
    TipFact fact = facts.findUnprocessed().get(0);
    assertEquals("artist-9", fact.artistId());
    assertEquals(450L, fact.creatorShareMinor(), "only the CREATOR share feeds analytics, not the gross");
    assertEquals("GHS", fact.currency());
    assertEquals(at, fact.occurredAt());
  }
}
