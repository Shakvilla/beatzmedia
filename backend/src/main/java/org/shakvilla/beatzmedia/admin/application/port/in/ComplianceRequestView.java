package org.shakvilla.beatzmedia.admin.application.port.in;

import java.time.Instant;

import org.shakvilla.beatzmedia.admin.domain.ComplianceRequest;
import org.shakvilla.beatzmedia.admin.domain.ComplianceStatus;

/**
 * Wire-shaped view of a compliance request, matching {@code ComplianceRequest} in {@code
 * Frontend/src/lib/admin-data.ts}: {@code { id, type, subject, detail, due, status }}. {@code due} is
 * the ISO-8601 {@code dueAt} (or {@code null}); {@code type}/{@code status} are the wire tokens. Admin
 * ADD §6 (LLFR-ADMIN-09.1).
 */
public record ComplianceRequestView(
    String id, String type, String subject, String detail, String due, String status) {

  /**
   * Projects a request, reporting {@code overdue} for anything past its deadline and not completed.
   *
   * <p>{@code overdue} is derived here rather than read from the stored status, which nothing ever
   * set — see {@link ComplianceRequest#isOverdue}. Reporting it through the existing {@code status}
   * token keeps the wire contract unchanged and makes the page's own counter and red styling work
   * without touching them.
   *
   * <p>The cost is that an in-progress request past its deadline reports {@code overdue} rather than
   * {@code in_progress}, so the In-progress tab will not list it. For a statutory-deadline queue
   * that is the right trade: a missed deadline is the more urgent fact about the request, and
   * hiding it behind "someone is on it" is how the counter came to read zero in the first place.
   */
  public static ComplianceRequestView of(ComplianceRequest r, Instant now) {
    return new ComplianceRequestView(
        r.getId(),
        r.getType().wireValue(),
        r.getSubjectRef(),
        r.getDetail(),
        r.getDueAt() != null ? r.getDueAt().toString() : null,
        r.isOverdue(now) ? ComplianceStatus.OVERDUE.wireValue() : r.getStatus().wireValue());
  }
}
