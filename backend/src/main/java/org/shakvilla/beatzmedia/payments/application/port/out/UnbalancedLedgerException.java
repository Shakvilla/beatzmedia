package org.shakvilla.beatzmedia.payments.application.port.out;

/**
 * Thrown when a ledger posting is not balanced (Σ DEBIT != Σ CREDIT, INV-6). Raised by
 * {@link LedgerRepository#postBalanced} in-app before any DB write, so a programming error surfaces
 * loudly and no partial/unbalanced state is ever attempted. The DB deferred constraint trigger
 * ({@code assert_txn_balanced}, V703) is the durable backstop under this check.
 */
public class UnbalancedLedgerException extends RuntimeException {

  public UnbalancedLedgerException(String message) {
    super(message);
  }
}
