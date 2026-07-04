package org.shakvilla.beatzmedia.commerce.application.port.in;

/**
 * Internal input port (Commerce ADD §4.1) — invoked <strong>only</strong> by the {@code OrderRefunded}
 * event handler (INV-9). No REST surface, no other caller: ownership is revoked solely on a completed
 * refund, mirroring how {@link GrantOwnership} grants solely on settlement (INV-1).
 *
 * <p>Revokes every ownership grant created for the refunded order: an album/season purchase revokes
 * ALL its constituent track/episode grants (mirroring the INV-2 grant expansion — the grants were
 * already materialised one-per-unit at grant time, so revocation simply revokes them all). The order
 * transitions {@code paid → refunded}. Idempotent on the order: a re-delivered refund event revokes
 * nothing further (grants already revoked; already-refunded order is a no-op). Commerce owns its
 * {@code ownership_grant} table; payments never touches it — the order reference travels on the event.
 */
public interface RevokeOwnership {

  /**
   * Revoke ownership for the order referenced by a completed refund.
   *
   * @param orderReference the {@code BZ-YYYY-NNNNN} reference carried by {@code OrderRefunded}
   */
  void revokeForRefundedOrder(String orderReference);
}
