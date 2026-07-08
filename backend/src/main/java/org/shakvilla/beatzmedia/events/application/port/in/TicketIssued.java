package org.shakvilla.beatzmedia.events.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.events.domain.TicketId;

/** Result of {@link IssueTicket#issue}. Events ADD §4.1. */
public record TicketIssued(List<TicketId> ticketIds, List<String> qrRefs, boolean tierNowSoldOut) {}
