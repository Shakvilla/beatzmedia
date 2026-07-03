package org.shakvilla.beatzmedia.payments.application.port.out;

/**
 * Thrown by {@link LedgerRepository#claimPosting} when a settlement posting for a given source
 * reference ({@code (refType, refId)}) already exists — i.e. a concurrent or replayed poster lost the
 * race to the {@code ledger_posting} UNIQUE header (finding F1). It signals a <em>benign idempotent
 * no-op</em>: the winning poster already credited exactly once, so the loser must roll back and do
 * nothing. Callers catch this, log at debug, and return success (never a 500) so a legitimate
 * concurrent double-delivery yields one credit, not two (INV-1/INV-6).
 */
public class DuplicatePostingException extends RuntimeException {

  private final String refType;
  private final String refId;

  public DuplicatePostingException(String refType, String refId, Throwable cause) {
    super("a ledger posting already exists for " + refType + "/" + refId, cause);
    this.refType = refType;
    this.refId = refId;
  }

  public String refType() {
    return refType;
  }

  public String refId() {
    return refId;
  }
}
