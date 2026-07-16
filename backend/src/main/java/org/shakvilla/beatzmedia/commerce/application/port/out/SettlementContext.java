package org.shakvilla.beatzmedia.commerce.application.port.out;

import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * The settled-line context a {@link SettlementSource#fulfill} needs to perform its owning-module side
 * effect (ticket mint / store stock): the line's {@code refId}, the settled {@link OrderId} (the
 * exactly-once key the owning module keys its idempotency on, e.g. {@code (orderId, tierId)}), the
 * buyer, and the line quantity. WU-COM-4.
 */
public record SettlementContext(String refId, OrderId orderId, AccountId buyer, int qty) {}
