package org.shakvilla.beatzmedia.commerce.application.port.out;

/**
 * Output port: allocates a collision-safe human order reference ({@code BZ-YYYY-NNNNN}). Commerce ADD
 * §7 (order_ref collision-safety carryover, WU-PLT-1 PR #10).
 *
 * <p>The implementing adapter draws the sequence number from a DB-backed {@code SEQUENCE}
 * ({@code order_ref_seq}, V944) so the counter survives JVM restarts and coordinates across
 * horizontally-scaled instances — replacing the unsafe in-memory {@code AtomicLong}. The {@code
 * uq_order_reference} UNIQUE constraint is the durable backstop that fails loudly on any residual
 * collision instead of silently duplicating a reference.
 */
public interface OrderRefGenerator {

  /** Allocate the next collision-safe order reference for the given year. */
  String nextReference(int year);
}
