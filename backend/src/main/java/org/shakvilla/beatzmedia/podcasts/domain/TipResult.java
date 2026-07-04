package org.shakvilla.beatzmedia.podcasts.domain;

/**
 * Value object returned by {@code TipShow}: the outcome of issuing a tip to a show (ADD §3). Carries
 * the resolved tip/intent id and a coarse status string mapped to the API-CONTRACT {@code
 * TipResponse} shape ({@code ACCEPTED | PROCESSING | SETTLED}). Framework-free.
 *
 * <p>No money math lives here: the 90/10 split (INV-4) is posted by the payments settlement machinery
 * (WU-PAY-3 {@code TipSettlementSubscriber} / {@code TipLedgerPoster}); this module only surfaces the
 * acknowledgement that the tip charge was accepted.
 */
public record TipResult(String tipId, String status) {

  public TipResult {
    if (tipId == null || tipId.isBlank()) {
      throw new IllegalArgumentException("tipId must not be blank");
    }
    if (status == null || status.isBlank()) {
      throw new IllegalArgumentException("status must not be blank");
    }
  }
}
