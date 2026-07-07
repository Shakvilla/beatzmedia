package org.shakvilla.beatzmedia.admin.fakes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.admin.application.port.out.SupportTicketRepository;
import org.shakvilla.beatzmedia.admin.domain.SupportTicket;
import org.shakvilla.beatzmedia.admin.domain.TicketStatus;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * In-memory fake for {@link SupportTicketRepository}. Testing-strategy §2.
 */
public class FakeSupportTicketRepository implements SupportTicketRepository {

  private final Map<String, SupportTicket> tickets = new LinkedHashMap<>();

  public void seed(SupportTicket ticket) {
    tickets.put(ticket.getId(), ticket);
  }

  @Override
  public Page<SupportTicket> list(TicketStatus status, String q, PageRequest page) {
    List<SupportTicket> filtered = tickets.values().stream()
        .filter(t -> status == null || t.getStatus() == status)
        .filter(t -> q == null || q.isBlank()
            || t.getSubject().toLowerCase().contains(q.toLowerCase())
            || t.getRequesterRef().toLowerCase().contains(q.toLowerCase()))
        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
        .toList();
    int from = Math.min(page.offset(), filtered.size());
    int to = Math.min(from + page.size(), filtered.size());
    return new Page<>(filtered.subList(from, to), page.page(), page.size(), filtered.size());
  }

  @Override
  public Optional<SupportTicket> findById(String ticketId) {
    return Optional.ofNullable(tickets.get(ticketId));
  }

  @Override
  public void save(SupportTicket ticket) {
    tickets.put(ticket.getId(), ticket);
  }

  public int size() {
    return tickets.size();
  }
}
