package org.shakvilla.beatzmedia.events.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link TicketRef} — the shared {@code "eventId:tierName"} parser (WU-COM-4). */
class TicketRefTest {

  @Test
  void parsesEventIdAndTierName() {
    TicketRef ref = TicketRef.parse("evt-1:VIP");
    assertEquals(new EventId("evt-1"), ref.eventId());
    assertEquals("VIP", ref.tierName());
  }

  @Test
  void preservesTierNameWithSpacesAndParens() {
    TicketRef ref = TicketRef.parse("iron-boy-live:VVIP Table (5)");
    assertEquals(new EventId("iron-boy-live"), ref.eventId());
    assertEquals("VVIP Table (5)", ref.tierName());
  }

  @Test
  void rejectsMissingColon() {
    assertThrows(IllegalArgumentException.class, () -> TicketRef.parse("no-colon"));
  }

  @Test
  void rejectsEmptyEventId() {
    assertThrows(IllegalArgumentException.class, () -> TicketRef.parse(":VIP"));
  }

  @Test
  void rejectsEmptyTierName() {
    assertThrows(IllegalArgumentException.class, () -> TicketRef.parse("evt-1:"));
  }

  @Test
  void rejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> TicketRef.parse(null));
  }
}
