package org.shakvilla.beatzmedia.admin.application.port.in;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Input port: paged ticket inbox. Returns the full {@link SupportTicketDetailView} (including the
 * message thread) per item — the frontend inbox renders a selected ticket's thread directly from
 * the list response with no extra fetch ({@code admin.support.tsx}). Auth: any admin (support
 * scope re-checked here per admin ADD §9.1). LLFR-ADMIN-08.1.
 */
public interface ListSupportTickets {

  Page<SupportTicketDetailView> list(String actorId, TicketQuery query, PageRequest page);
}
