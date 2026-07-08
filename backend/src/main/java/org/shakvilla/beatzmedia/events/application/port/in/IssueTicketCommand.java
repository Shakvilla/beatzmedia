package org.shakvilla.beatzmedia.events.application.port.in;

import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.events.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.events.domain.OrderId;
import org.shakvilla.beatzmedia.events.domain.TicketTierId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Value passed by {@code commerce} on settlement of a ticket cart line; {@code refId
 * "eventId:tier"} is parsed by commerce into {@code eventId}/{@code tierId} before calling this
 * port. Events ADD §4.1.
 */
public record IssueTicketCommand(
    EventId eventId,
    TicketTierId tierId,
    OrderId orderId,
    AccountId holderAccountId,
    String holderName,
    int quantity,
    IdempotencyKey key) {}
