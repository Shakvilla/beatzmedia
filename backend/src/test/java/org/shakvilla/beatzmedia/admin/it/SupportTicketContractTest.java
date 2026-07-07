package org.shakvilla.beatzmedia.admin.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.SupportMessageDto;
import org.shakvilla.beatzmedia.admin.adapter.in.rest.SupportTicketDto;
import org.shakvilla.beatzmedia.admin.application.port.in.SupportMessageView;
import org.shakvilla.beatzmedia.admin.application.port.in.SupportTicketDetailView;
import org.shakvilla.beatzmedia.admin.domain.TicketPriority;
import org.shakvilla.beatzmedia.admin.domain.TicketStatus;

/**
 * Contract test: verifies that {@link SupportTicketDto} / {@link SupportMessageDto} are
 * structurally compatible with {@code SupportTicket} / {@code SupportMessage} in {@code
 * Frontend/src/lib/admin-data.ts} and API-CONTRACT §12 (Support).
 *
 * <pre>
 * SupportMessage { id, from: 'user'|'agent', author, text, time }
 * SupportTicket  { id, subject, requester, channel, priority: 'high'|'normal'|'low',
 *                  status: 'open'|'pending'|'resolved', age, messages: SupportMessage[] }
 * </pre>
 *
 * Admin ADD §6 / DoD §11 contract-test requirement (LLFR-ADMIN-08.1).
 */
@Tag("unit")
class SupportTicketContractTest {

  private static final Set<String> VALID_STATUSES = Set.of("open", "pending", "resolved");
  private static final Set<String> VALID_PRIORITIES = Set.of("high", "normal", "low");
  private static final Set<String> VALID_FROM = Set.of("user", "agent");

  @Test
  void support_ticket_dto_field_names_match_contract() {
    var names = new java.util.HashSet<String>();
    for (var c : SupportTicketDto.class.getRecordComponents()) {
      names.add(c.getName());
    }
    assertTrue(names.contains("id"), "must have 'id'");
    assertTrue(names.contains("subject"), "must have 'subject'");
    assertTrue(names.contains("requester"), "must have 'requester'");
    assertTrue(names.contains("channel"), "must have 'channel'");
    assertTrue(names.contains("priority"), "must have 'priority'");
    assertTrue(names.contains("status"), "must have 'status'");
    assertTrue(names.contains("age"), "must have 'age'");
    assertTrue(names.contains("messages"), "must have 'messages'");
    assertEquals(8, names.size(), "no extra fields beyond the contract shape");
  }

  @Test
  void support_message_dto_field_names_match_contract() {
    var names = new java.util.HashSet<String>();
    for (var c : SupportMessageDto.class.getRecordComponents()) {
      names.add(c.getName());
    }
    assertTrue(names.contains("id"), "must have 'id'");
    assertTrue(names.contains("from"), "must have 'from'");
    assertTrue(names.contains("author"), "must have 'author'");
    assertTrue(names.contains("text"), "must have 'text'");
    assertTrue(names.contains("time"), "must have 'time'");
    assertEquals(5, names.size(), "no extra fields beyond the contract shape");
  }

  @Test
  void support_ticket_dto_has_all_required_fields_populated() {
    Instant now = Instant.parse("2026-07-07T10:00:00Z");
    SupportMessageView message = new SupportMessageView("m1", "user", "Fan", "help", now);
    SupportTicketDetailView view = new SupportTicketDetailView(
        "t1", "Subject", "Requester", "email", "high", "open", now, List.of(message));

    SupportTicketDto dto = SupportTicketDto.from(view);

    assertEquals("t1", dto.id());
    assertEquals("Subject", dto.subject());
    assertEquals("Requester", dto.requester());
    assertEquals("email", dto.channel());
    assertEquals("high", dto.priority());
    assertEquals("open", dto.status());
    assertEquals(now.toString(), dto.age(), "age is an ISO-8601 string, not pre-formatted");
    assertEquals(1, dto.messages().size());
  }

  @Test
  void support_message_dto_time_is_iso8601_not_preformatted() {
    Instant now = Instant.parse("2026-07-07T10:00:00Z");
    SupportMessageView view = new SupportMessageView("m1", "agent", "Yaa", "hi", now);
    SupportMessageDto dto = SupportMessageDto.from(view);

    assertEquals(now.toString(), dto.time());
    assertTrue(dto.time().matches("^\\d{4}-\\d{2}-\\d{2}T.*Z$"), "time must be ISO-8601");
  }

  @Test
  void all_ticket_status_wire_values_are_in_contract_set() {
    for (TicketStatus s : TicketStatus.values()) {
      assertTrue(VALID_STATUSES.contains(s.wireValue()),
          "TicketStatus." + s.name() + " wire value not in contract set: " + s.wireValue());
    }
    assertEquals(VALID_STATUSES.size(), TicketStatus.values().length);
  }

  @Test
  void all_ticket_priority_wire_values_are_in_contract_set() {
    for (TicketPriority p : TicketPriority.values()) {
      assertTrue(VALID_PRIORITIES.contains(p.wireValue()),
          "TicketPriority." + p.name() + " wire value not in contract set: " + p.wireValue());
    }
    assertEquals(VALID_PRIORITIES.size(), TicketPriority.values().length);
  }

  @Test
  void message_from_dto_values_are_in_contract_set() {
    Instant now = Instant.now();
    assertTrue(VALID_FROM.contains(
        SupportMessageDto.from(new SupportMessageView("m1", "user", "A", "t", now)).from()));
    assertTrue(VALID_FROM.contains(
        SupportMessageDto.from(new SupportMessageView("m2", "agent", "A", "t", now)).from()));
  }
}
