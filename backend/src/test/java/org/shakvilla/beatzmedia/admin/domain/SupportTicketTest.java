package org.shakvilla.beatzmedia.admin.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link SupportTicket} aggregate. Pure Java, no fakes needed. Testing-strategy
 * §2 / admin ADD §3 (LLFR-ADMIN-08.1).
 */
@Tag("unit")
class SupportTicketTest {

  private static final Instant NOW = Instant.parse("2026-07-07T10:00:00Z");

  private SupportTicket newOpenTicket() {
    return new SupportTicket(
        "t1",
        "Payout not received",
        "account-1",
        "email",
        TicketPriority.HIGH,
        TicketStatus.OPEN,
        null,
        NOW,
        List.of());
  }

  @Test
  void reply_appends_message_and_moves_open_to_pending() {
    SupportTicket ticket = newOpenTicket();
    SupportMessage msg = ticket.reply("m1", "Yaa (Support)", "Looking into it.", NOW.plusSeconds(60));

    assertEquals(1, ticket.getMessages().size());
    assertEquals(msg, ticket.getMessages().get(0));
    assertEquals(TicketStatus.PENDING, ticket.getStatus());
    assertEquals(MessageFrom.AGENT, msg.getFrom());
  }

  @Test
  void reply_with_blank_text_throws_BlankReplyException_before_any_mutation() {
    SupportTicket ticket = newOpenTicket();
    assertThrows(BlankReplyException.class, () -> ticket.reply("m1", "Yaa", "  ", NOW));
    assertTrue(ticket.getMessages().isEmpty(), "no message appended on blank reply");
    assertEquals(TicketStatus.OPEN, ticket.getStatus(), "status unchanged on blank reply");
  }

  @Test
  void reply_with_null_text_throws_BlankReplyException() {
    SupportTicket ticket = newOpenTicket();
    assertThrows(BlankReplyException.class, () -> ticket.reply("m1", "Yaa", null, NOW));
  }

  @Test
  void reply_on_resolved_ticket_does_not_reopen_status() {
    SupportTicket ticket = newOpenTicket();
    ticket.resolve();
    ticket.reply("m1", "Yaa", "Following up.", NOW.plusSeconds(60));
    assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
  }

  @Test
  void assign_sets_assignee_id() {
    SupportTicket ticket = newOpenTicket();
    ticket.assign("member-1");
    assertEquals("member-1", ticket.getAssigneeId());
  }

  @Test
  void assign_with_blank_id_throws() {
    SupportTicket ticket = newOpenTicket();
    assertThrows(IllegalArgumentException.class, () -> ticket.assign(" "));
  }

  @Test
  void resolve_transitions_open_to_resolved() {
    SupportTicket ticket = newOpenTicket();
    ticket.resolve();
    assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
  }

  @Test
  void resolve_twice_throws_TicketAlreadyResolvedException() {
    SupportTicket ticket = newOpenTicket();
    ticket.resolve();
    assertThrows(TicketAlreadyResolvedException.class, ticket::resolve);
  }

  @Test
  void ticket_status_wire_values_are_lowercase() {
    assertEquals("open", TicketStatus.OPEN.wireValue());
    assertEquals("pending", TicketStatus.PENDING.wireValue());
    assertEquals("resolved", TicketStatus.RESOLVED.wireValue());
  }

  @Test
  void ticket_priority_wire_values_are_lowercase() {
    assertEquals("high", TicketPriority.HIGH.wireValue());
    assertEquals("normal", TicketPriority.NORMAL.wireValue());
    assertEquals("low", TicketPriority.LOW.wireValue());
  }

  @Test
  void ticket_status_from_wire_value_parses_all() {
    assertEquals(TicketStatus.OPEN, TicketStatus.fromWireValue("open"));
    assertEquals(TicketStatus.PENDING, TicketStatus.fromWireValue("pending"));
    assertEquals(TicketStatus.RESOLVED, TicketStatus.fromWireValue("resolved"));
  }

  @Test
  void ticket_status_from_invalid_wire_value_throws() {
    assertThrows(InvalidTicketStatusException.class, () -> TicketStatus.fromWireValue("bogus"));
  }

  @Test
  void ticket_status_from_null_wire_value_throws() {
    assertThrows(InvalidTicketStatusException.class, () -> TicketStatus.fromWireValue(null));
  }

  @Test
  void ticket_priority_from_invalid_wire_value_throws() {
    assertThrows(InvalidTicketStatusException.class, () -> TicketPriority.fromWireValue("urgent"));
  }

  @Test
  void constructing_with_blank_subject_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SupportTicket(
            "t1", " ", "account-1", "email", TicketPriority.LOW, TicketStatus.OPEN, null, NOW,
            List.of()));
  }
}
