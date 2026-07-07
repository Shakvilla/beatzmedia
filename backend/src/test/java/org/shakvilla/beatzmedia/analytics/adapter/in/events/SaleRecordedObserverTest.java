package org.shakvilla.beatzmedia.analytics.adapter.in.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.analytics.domain.SaleFact;
import org.shakvilla.beatzmedia.analytics.fakes.InMemorySaleFactRepository;
import org.shakvilla.beatzmedia.commerce.domain.SaleRecorded;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/** Unit test for {@link SaleRecordedObserver} — stages one fact per observed event. */
@Tag("unit")
class SaleRecordedObserverTest {

  @Test
  void onSaleRecorded_stagesOneFactWithTheEventsSnapshot() {
    InMemorySaleFactRepository facts = new InMemorySaleFactRepository();
    SaleRecordedObserver observer = new SaleRecordedObserver(facts, FakeIds.sequential("fact"));
    Instant at = Instant.parse("2026-07-05T10:00:00Z");

    observer.onSaleRecorded(new SaleRecorded("order-1", "artist-9", 1000L, "GHS", at));

    assertEquals(1, facts.findUnprocessed().size());
    SaleFact fact = facts.findUnprocessed().get(0);
    assertEquals("artist-9", fact.artistId());
    assertEquals(1000L, fact.grossMinor());
    assertEquals("GHS", fact.currency());
    assertEquals(at, fact.occurredAt());
    assertEquals(false, fact.processed());
  }
}
