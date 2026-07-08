package org.shakvilla.beatzmedia.events.application.port.out;

import java.util.Set;

import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.events.domain.TicketTierId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Reads the caller's already-held tickets for a given event, purely for potential per-caller
 * decoration (Events ADD §1 / §2 C4 diagram). Backed by this module's OWN {@code ticket} table —
 * unlike other modules' ownership readers, this does NOT cross-call another module: the canonical
 * ownership grant lives in {@code commerce}, but the ticket rows minted on settlement are this
 * module's own persistence. Currently unused by {@code ListEvents}/{@code GetEvent} (neither the
 * {@code Event} nor {@code TicketTier} wire DTO carries an ownership field per {@code
 * Frontend/src/types/index.ts}); provisioned per the ADD for future callers. Events ADD §4.2.
 */
public interface OwnershipReader {

  Set<TicketTierId> heldTiersFor(AccountId account, EventId event);
}
