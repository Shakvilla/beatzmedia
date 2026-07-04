package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;

/**
 * Domain event published (AFTER_SUCCESS) when a dispute refund completes (INV-9). This is the
 * <strong>only</strong> sanctioned trigger for downstream ownership revocation: {@code commerce}
 * (WU-COM-2 / WU-PAY-5) consumes it to revoke the {@link OwnershipGrant}s created for the refunded
 * order — album/season refunds revoke ALL constituent track/episode grants (mirroring the INV-2
 * expansion). The payments module never touches commerce's ownership tables itself; it only publishes
 * this event carrying the order reference commerce resolves against its own tables.
 *
 * <p>Carries only ids + a minimal money snapshot (minor units, INV-11) — never a JPA entity. Emitted
 * <strong>exactly once</strong> per refund: the {@code refund}/{@code dispute} transition is guarded
 * and the clawback claim ({@code ledger_posting} header keyed by the refund id) makes a re-delivered
 * refund a no-op, so a duplicate refund/chargeback event never double-revokes ownership (INV-9).
 */
public record OrderRefunded(
    String disputeId,
    String refundId,
    String orderRef,
    String paymentIntentId,
    long amountMinor,
    String currency,
    Instant refundedAt) {}
