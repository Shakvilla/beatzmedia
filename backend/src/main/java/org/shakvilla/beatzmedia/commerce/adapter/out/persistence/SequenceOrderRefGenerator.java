package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.commerce.application.port.out.OrderRefGenerator;

/**
 * {@link OrderRefGenerator} backed by the {@code order_ref_seq} Postgres SEQUENCE (V944). Replaces the
 * unsafe in-memory {@code AtomicLong} in {@code Uuidv7IdGenerator.newOrderRef()} (WU-PLT-1 PR #10
 * carryover): the DB sequence is monotonic across JVM restarts and horizontally-scaled instances, so
 * two nodes can never mint the same {@code NNNNN}. The {@code uq_order_reference} UNIQUE constraint on
 * {@code "order".reference} is the durable backstop that fails loudly on any residual collision.
 *
 * <p>The reference format is {@code BZ-YYYY-NNNNN} (5-digit zero-padded, widening beyond 5 digits once
 * the sequence exceeds 99999 — the column is {@code VARCHAR(24)} to allow growth without truncation).
 */
@ApplicationScoped
public class SequenceOrderRefGenerator implements OrderRefGenerator {

  private final EntityManager em;

  @Inject
  public SequenceOrderRefGenerator(EntityManager em) {
    this.em = em;
  }

  @Override
  public String nextReference(int year) {
    Number seq = (Number) em.createNativeQuery("SELECT nextval('order_ref_seq')").getSingleResult();
    return String.format("BZ-%04d-%05d", year, seq.longValue());
  }
}
