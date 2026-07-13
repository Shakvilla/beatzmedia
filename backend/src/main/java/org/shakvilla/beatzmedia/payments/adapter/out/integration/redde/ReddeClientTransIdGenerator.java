package org.shakvilla.beatzmedia.payments.adapter.out.integration.redde;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Supplies Redde {@code clienttransid} values (WU-PAY-6). Redde caps this id at 10 digits, so the
 * app's UUIDv7 ids are unusable. A dedicated Postgres {@code redde_clienttransid_seq} (V966) provides
 * short, monotonically-increasing numeric ids that survive JVM restarts (unlike an in-memory
 * counter, which would reset and risk collisions — the bug class already flagged for order refs in
 * WU-COM-1). {@code nextval} is non-transactional in Postgres, so no id is ever reused on rollback.
 */
@ApplicationScoped
public class ReddeClientTransIdGenerator {

  /** 10^10 — keeps the id at most 10 digits (the sequence wraps well below any real volume). */
  private static final long TEN_DIGIT_MODULUS = 10_000_000_000L;

  private final EntityManager em;

  @Inject
  public ReddeClientTransIdGenerator(EntityManager em) {
    this.em = em;
  }

  /** Next {@code clienttransid} as a decimal string of at most 10 digits. */
  @Transactional
  public String next() {
    Number value =
        (Number) em.createNativeQuery("SELECT nextval('redde_clienttransid_seq')").getSingleResult();
    return Long.toString(value.longValue() % TEN_DIGIT_MODULUS);
  }
}
