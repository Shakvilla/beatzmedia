package org.shakvilla.beatzmedia.payments.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.in.DisputeView;
import org.shakvilla.beatzmedia.payments.domain.Dispute;
import org.shakvilla.beatzmedia.payments.domain.DisputeEvent;
import org.shakvilla.beatzmedia.payments.domain.DisputeId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Contract test: {@link DisputeView} matches the frontend {@code Dispute} shape
 * ({@code Frontend/src/lib/admin-data.ts}):
 *
 * <pre>
 * Dispute { id, kind, subject, detail, amount?: Money, status, timeline: { id, text, time }[] }
 * status: 'open' | 'refunded' | 'rejected' | 'escalated'
 * Money  { amount: number (decimal cedis), currency: "GHS" }
 * </pre>
 *
 * Money is wire decimal cedis + currency (INV-11); the status is a raw wire enum, never a label.
 * DoD §11 contract-test requirement.
 */
@Tag("unit")
class DisputeContractTest {

  private static final Set<String> VALID_STATUSES =
      Set.of("open", "refunded", "rejected", "escalated");

  @Test
  void disputeView_matchesFrontendShape() {
    Dispute dispute =
        Dispute.open(
            new DisputeId("d-1"),
            "BZ-2026-0481",
            "intent-1",
            "Chargeback",
            "order BZ-2026-0481",
            "Card · ₵180",
            Money.ofMinor(18000, Currency.GHS),
            true,
            Instant.parse("2026-04-25T10:15:30Z"));

    DisputeView view =
        DisputeView.of(
            dispute,
            List.of(
                DisputeEvent.of(
                    "t1", new DisputeId("d-1"), "Dispute opened by fan", "provider",
                    Instant.parse("2026-04-25T10:15:30Z"))));

    assertEquals("d-1", view.id());
    assertEquals("Chargeback", view.kind());
    assertEquals("order BZ-2026-0481", view.subject());
    assertEquals("Card · ₵180", view.detail());
    assertNotNull(view.amount(), "amount present");
    assertEquals("GHS", view.amount().currency());
    // 18000 pesewas → 180.00 cedis on the wire (INV-11).
    assertEquals(0, view.amount().amount().compareTo(new java.math.BigDecimal("180.00")));
    assertTrue(VALID_STATUSES.contains(view.status()), "status is a raw wire enum: " + view.status());
    assertEquals("open", view.status());
    assertEquals(1, view.timeline().size());
    assertEquals("Dispute opened by fan", view.timeline().get(0).text());
    assertNotNull(view.timeline().get(0).time(), "timeline time is ISO-8601");
  }
}
