package org.shakvilla.beatzmedia.events.application.port.in;

/**
 * INTERNAL — invoked by {@code commerce} on settlement of a ticket line. NOT REST-exposed. Mints a
 * {@code Ticket}, decrements the tier under a row lock, and (by leaving the read-time status
 * derivation to do its job, INV-EVT-2) transitions the event to sold-out once its last tier sells
 * out. LLFR-COMMERCE-02.5 / PRD §6.5.5 / OQ-11. Events ADD §4.1.
 */
public interface IssueTicket {

  TicketIssued issue(IssueTicketCommand command);
}
