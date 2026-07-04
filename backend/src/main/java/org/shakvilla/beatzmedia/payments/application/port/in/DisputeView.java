package org.shakvilla.beatzmedia.payments.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.payments.domain.Dispute;
import org.shakvilla.beatzmedia.payments.domain.DisputeEvent;

/**
 * Read model for a dispute detail + timeline (LLFR-PAYMENTS-04.1). Matches the {@code Dispute} shape
 * in {@code Frontend/src/lib/admin-data.ts} ({@code id, kind, subject, detail, amount?, status,
 * timeline[]}). Money is wire {@code { amount, currency }} (INV-11). No domain type crosses the port.
 */
public record DisputeView(
    String id,
    String kind,
    String subject,
    String detail,
    MoneyView amount,
    String status,
    String opened,
    List<TimelineEntryView> timeline) {

  /** A single timeline entry — matches the frontend {@code TimelineEntry} ({@code id, text, time}). */
  public record TimelineEntryView(String id, String text, String time) {}

  public static DisputeView of(Dispute dispute, List<DisputeEvent> events) {
    List<TimelineEntryView> timeline =
        events.stream()
            .map(e -> new TimelineEntryView(e.id(), e.text(), e.at() != null ? e.at().toString() : null))
            .toList();
    return new DisputeView(
        dispute.getId().value(),
        dispute.getKind(),
        dispute.getSubject(),
        dispute.getDetail(),
        MoneyView.of(dispute.getAmount()),
        dispute.getStatus().wire(),
        dispute.getOpenedAt() != null ? dispute.getOpenedAt().toString() : null,
        timeline);
  }
}
