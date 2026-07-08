package org.shakvilla.beatzmedia.events.adapter.out.persistence;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.events.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.events.domain.TicketTierId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Implements {@link OwnershipReader} by querying this module's own {@code ticket} table (no
 * cross-module call — unlike other modules' ownership readers, the ticket rows are minted directly
 * into this module's persistence on settlement). Events ADD §4.2 / §5.2.
 */
@ApplicationScoped
public class TicketOwnershipReaderAdapter implements OwnershipReader {

  private final EntityManager em;

  @Inject
  public TicketOwnershipReaderAdapter(EntityManager em) {
    this.em = em;
  }

  @Override
  public Set<TicketTierId> heldTiersFor(AccountId account, EventId event) {
    List<String> tierIds =
        em.createQuery(
                "SELECT DISTINCT t.tierId FROM TicketEntity t"
                    + " WHERE t.holderAccountId = :accountId AND t.eventId = :eventId",
                String.class)
            .setParameter("accountId", account.value())
            .setParameter("eventId", event.value())
            .getResultList();
    return tierIds.stream().map(TicketTierId::new).collect(Collectors.toUnmodifiableSet());
  }
}
