package org.shakvilla.beatzmedia.admin.application.port.in;

import org.shakvilla.beatzmedia.admin.domain.TicketStatus;

/**
 * Filter for {@link ListSupportTickets}. Both fields are optional (nullable = no filter). Admin
 * ADD §5.1 / LLFR-ADMIN-08.1 ({@code GET /v1/admin/support/tickets?status=&q=}).
 */
public record TicketQuery(TicketStatus status, String q) {

  public static TicketQuery all() {
    return new TicketQuery(null, null);
  }
}
