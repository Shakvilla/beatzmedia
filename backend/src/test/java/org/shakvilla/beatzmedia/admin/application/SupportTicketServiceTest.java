package org.shakvilla.beatzmedia.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.in.SupportTicketDetailView;
import org.shakvilla.beatzmedia.admin.application.port.in.TicketQuery;
import org.shakvilla.beatzmedia.admin.application.service.AssignTicketService;
import org.shakvilla.beatzmedia.admin.application.service.GetSupportTicketService;
import org.shakvilla.beatzmedia.admin.application.service.ListSupportTicketsService;
import org.shakvilla.beatzmedia.admin.application.service.ReplyToTicketService;
import org.shakvilla.beatzmedia.admin.application.service.ResolveTicketService;
import org.shakvilla.beatzmedia.admin.domain.BlankReplyException;
import org.shakvilla.beatzmedia.admin.domain.MessageFrom;
import org.shakvilla.beatzmedia.admin.domain.SupportMessage;
import org.shakvilla.beatzmedia.admin.domain.SupportTicket;
import org.shakvilla.beatzmedia.admin.domain.TicketAlreadyResolvedException;
import org.shakvilla.beatzmedia.admin.domain.TicketNotFoundException;
import org.shakvilla.beatzmedia.admin.domain.TicketPriority;
import org.shakvilla.beatzmedia.admin.domain.TicketStatus;
import org.shakvilla.beatzmedia.admin.fakes.FakeIdentityReader;
import org.shakvilla.beatzmedia.admin.fakes.FakeSupportTicketRepository;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for the WU-ADM-7 support-ticket use-case services. Uses fakes for all output ports.
 * One test per LLFR-ADMIN-08.1 acceptance criterion. Testing-strategy §2 / admin ADD §11.
 */
@Tag("unit")
class SupportTicketServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-07T10:00:00Z");
  private static final String ACTOR_ID = "account-agent";

  private FakeSupportTicketRepository repo;
  private FakeIdentityReader identityReader;
  private FakeAuditWriter auditWriter;
  private FakeIds ids;
  private FakeClock clock;

  private ListSupportTicketsService listService;
  private GetSupportTicketService getService;
  private ReplyToTicketService replyService;
  private AssignTicketService assignService;
  private ResolveTicketService resolveService;

  @BeforeEach
  void setUp() {
    repo = new FakeSupportTicketRepository();
    identityReader = new FakeIdentityReader();
    auditWriter = new FakeAuditWriter();
    ids = FakeIds.sequential("msg");
    clock = FakeClock.at(NOW);

    listService = new ListSupportTicketsService(repo, identityReader);
    getService = new GetSupportTicketService(repo, identityReader);
    replyService = new ReplyToTicketService(repo, identityReader, auditWriter, ids, clock);
    assignService = new AssignTicketService(repo, identityReader, auditWriter, ids, clock);
    resolveService = new ResolveTicketService(repo, identityReader, auditWriter, ids, clock);

    identityReader.seed("account-1", "Black Sherif");
    identityReader.seed(ACTOR_ID, "Yaa (Support)");

    repo.seed(new SupportTicket(
        "t1", "Payout not received", "account-1", "email",
        TicketPriority.HIGH, TicketStatus.OPEN, null, NOW, List.of()));
    repo.seed(new SupportTicket(
        "t2", "Can't upload WAV", "account-1", "in-app",
        TicketPriority.NORMAL, TicketStatus.RESOLVED, null, NOW.minusSeconds(3600), List.of()));
  }

  // ---- ListSupportTickets (LLFR-ADMIN-08.1: GET /admin/support/tickets?status=&q=) ----

  @Test
  void list_returns_all_tickets_with_resolved_display_names() {
    Page<SupportTicketDetailView> page = listService.list(ACTOR_ID, TicketQuery.all(), PageRequest.defaults());
    assertEquals(2, page.items().size());
    assertTrue(page.items().stream().allMatch(t -> t.requester().equals("Black Sherif")));
  }

  @Test
  void list_filters_by_status() {
    Page<SupportTicketDetailView> page =
        listService.list(ACTOR_ID, new TicketQuery(TicketStatus.OPEN, null), PageRequest.defaults());
    assertEquals(1, page.items().size());
    assertEquals("t1", page.items().get(0).id());
  }

  @Test
  void list_filters_by_free_text_query() {
    Page<SupportTicketDetailView> page =
        listService.list(ACTOR_ID, new TicketQuery(null, "WAV"), PageRequest.defaults());
    assertEquals(1, page.items().size());
    assertEquals("t2", page.items().get(0).id());
  }

  @Test
  void list_falls_back_to_raw_requesterRef_when_identity_lookup_misses() {
    repo.seed(new SupportTicket(
        "t3", "Unknown requester", "ghost-account", "email",
        TicketPriority.LOW, TicketStatus.OPEN, null, NOW, List.of()));
    Page<SupportTicketDetailView> page =
        listService.list(ACTOR_ID, new TicketQuery(null, "Unknown"), PageRequest.defaults());
    assertEquals("ghost-account", page.items().get(0).requester());
  }

  // ---- GetSupportTicket (LLFR-ADMIN-08.1: GET /admin/support/tickets/:id — thread) ----

  @Test
  void get_returns_ticket_detail_with_thread() {
    SupportTicketDetailView view = getService.get(ACTOR_ID, "t1");
    assertEquals("t1", view.id());
    assertEquals("Payout not received", view.subject());
    assertTrue(view.messages().isEmpty());
  }

  @Test
  void get_unknown_id_throws_TicketNotFoundException() {
    assertThrows(TicketNotFoundException.class, () -> getService.get(ACTOR_ID, "no-such-ticket"));
  }

  // ---- ReplyToTicket (LLFR-ADMIN-08.1: POST /admin/support/tickets/:id/reply { text }) ----

  @Test
  void reply_appends_message_and_audits_exactly_once() {
    var message = replyService.reply(ACTOR_ID, "t1", "We're on it.");
    assertEquals("We're on it.", message.text());
    assertEquals("agent", message.from());
    assertEquals("Yaa (Support)", message.author());

    SupportTicket ticket = repo.findById("t1").orElseThrow();
    assertEquals(1, ticket.getMessages().size());
    assertEquals(TicketStatus.PENDING, ticket.getStatus());
    assertEquals(1, auditWriter.size(), "exactly one AuditEntry per mutation (INV-10)");
    assertEquals(AuditType.USER, auditWriter.all().get(0).getType());
  }

  @Test
  void reply_with_blank_text_returns_422_before_any_state_change_or_audit() {
    assertThrows(BlankReplyException.class, () -> replyService.reply(ACTOR_ID, "t1", "   "));
    SupportTicket ticket = repo.findById("t1").orElseThrow();
    assertTrue(ticket.getMessages().isEmpty(), "no message appended on blank reply");
    assertEquals(TicketStatus.OPEN, ticket.getStatus(), "status unchanged on blank reply");
    assertEquals(0, auditWriter.size(), "no audit row on a rejected mutation");
  }

  @Test
  void reply_with_null_text_returns_422() {
    assertThrows(BlankReplyException.class, () -> replyService.reply(ACTOR_ID, "t1", null));
  }

  @Test
  void reply_to_unknown_ticket_throws_TicketNotFoundException() {
    assertThrows(TicketNotFoundException.class,
        () -> replyService.reply(ACTOR_ID, "no-such-ticket", "hello"));
  }

  @Test
  void reply_falls_back_to_actor_id_when_identity_lookup_misses() {
    var message = replyService.reply("ghost-agent", "t1", "hi");
    assertEquals("ghost-agent", message.author());
  }

  // ---- AssignTicket (LLFR-ADMIN-08.1: POST /admin/support/tickets/:id/assign) ----

  @Test
  void assign_sets_assignee_and_audits_exactly_once() {
    SupportTicketDetailView view = assignService.assign(ACTOR_ID, "t1", "member-42");
    assertEquals("t1", view.id());
    SupportTicket ticket = repo.findById("t1").orElseThrow();
    assertEquals("member-42", ticket.getAssigneeId());
    assertEquals(1, auditWriter.size());
  }

  @Test
  void assign_unknown_ticket_throws_TicketNotFoundException() {
    assertThrows(TicketNotFoundException.class,
        () -> assignService.assign(ACTOR_ID, "no-such-ticket", "member-1"));
  }

  // ---- ResolveTicket (LLFR-ADMIN-08.1: POST /admin/support/tickets/:id/resolve) ----

  @Test
  void resolve_transitions_status_and_audits_exactly_once() {
    SupportTicketDetailView view = resolveService.resolve(ACTOR_ID, "t1");
    assertEquals("resolved", view.status());
    assertEquals(TicketStatus.RESOLVED, repo.findById("t1").orElseThrow().getStatus());
    assertEquals(1, auditWriter.size());
  }

  @Test
  void resolve_already_resolved_ticket_throws_409_before_any_extra_audit() {
    assertThrows(TicketAlreadyResolvedException.class, () -> resolveService.resolve(ACTOR_ID, "t2"));
    assertEquals(0, auditWriter.size(), "no audit row on a rejected (already-resolved) mutation");
  }

  @Test
  void resolve_unknown_ticket_throws_TicketNotFoundException() {
    assertThrows(TicketNotFoundException.class,
        () -> resolveService.resolve(ACTOR_ID, "no-such-ticket"));
  }

  // ---- Domain message construction sanity (from/author/text mapping) ----

  @Test
  void support_message_from_wire_values_are_lowercase() {
    SupportMessage msg = new SupportMessage("m1", "t1", MessageFrom.USER, "Fan", "hi", NOW);
    assertEquals("user", msg.getFrom().wireValue());
  }
}
