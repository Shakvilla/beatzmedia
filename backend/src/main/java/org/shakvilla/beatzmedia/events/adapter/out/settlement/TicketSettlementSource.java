package org.shakvilla.beatzmedia.events.adapter.out.settlement;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.out.SettlementContext;
import org.shakvilla.beatzmedia.commerce.application.port.out.SettlementSource;
import org.shakvilla.beatzmedia.events.application.port.in.IssueTicket;
import org.shakvilla.beatzmedia.events.application.port.in.IssueTicketCommand;
import org.shakvilla.beatzmedia.events.application.port.out.EventRepository;
import org.shakvilla.beatzmedia.events.domain.Event;
import org.shakvilla.beatzmedia.events.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.events.domain.TicketRef;
import org.shakvilla.beatzmedia.events.domain.TicketTier;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Settlement for a purchased {@code ticket} (WU-COM-4): the payee is the event's organizer/artist
 * ({@code event.artist_id}); fulfillment mints the ticket(s) via the already-idempotent {@code
 * IssueTicket} port (decrements the tier under a row lock). A ticket is NOT an ownership grant, so
 * {@link #ownedEpisodeIds} stays empty.
 *
 * <p>The {@code refId} is parsed to {@code (eventId, tierName)} via {@link TicketRef} — the SAME
 * parser the price source uses, so the tier priced at checkout is the tier minted here. {@code
 * IssueTicketService} runs {@code @Transactional}, joining commerce's grant transaction, so a mint
 * failure rolls the whole settlement back and is retried by the next delivery; its own {@code
 * ticketExistsForOrderTier(orderId, tierId)} guard makes a redelivery a no-op.
 */
@ApplicationScoped
public class TicketSettlementSource implements SettlementSource {

  private final EventRepository repository;
  private final IssueTicket issueTicket;

  @Inject
  public TicketSettlementSource(EventRepository repository, IssueTicket issueTicket) {
    this.repository = repository;
    this.issueTicket = issueTicket;
  }

  @Override
  public String entityType() {
    return "ticket";
  }

  @Override
  public Optional<AccountId> payee(String refId) {
    TicketRef ref;
    try {
      ref = TicketRef.parse(refId);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    return repository.findById(ref.eventId()).flatMap(Event::artistId).map(AccountId::new);
  }

  @Override
  public void fulfill(SettlementContext ctx) {
    TicketRef ref = TicketRef.parse(ctx.refId());
    Event event =
        repository
            .findById(ref.eventId())
            .orElseThrow(
                () -> new IllegalStateException("ticket settlement: event not found " + ctx.refId()));
    TicketTier tier =
        event.tiers().stream()
            .filter(t -> t.name().equals(ref.tierName()))
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("ticket settlement: tier not found " + ctx.refId()));

    // holderName: the buyer's account id (a person-name lookup into identity is out of scope here;
    // IssueTicket only needs a stable holder reference). Improve to a real display name if needed.
    issueTicket.issue(
        new IssueTicketCommand(
            ref.eventId(),
            tier.id(),
            new org.shakvilla.beatzmedia.events.domain.OrderId(ctx.orderId().value()),
            ctx.buyer(),
            ctx.buyer().value(),
            ctx.qty(),
            new IdempotencyKey(ctx.orderId().value() + ":" + tier.id().value())));
  }
}
