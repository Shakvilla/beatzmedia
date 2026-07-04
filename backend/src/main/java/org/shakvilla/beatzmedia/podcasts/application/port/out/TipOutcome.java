package org.shakvilla.beatzmedia.podcasts.application.port.out;

/**
 * Result of an {@link IssueTipUseCase} call: the payments-issued tip/intent id plus the intent's
 * raw status string. Podcasts maps this to the API-CONTRACT {@code TipResponse.status}
 * ({@code ACCEPTED | PROCESSING | SETTLED}) in {@code TipShowService}. Framework-free. ADD §4.2.
 */
public record TipOutcome(String tipId, String status) {

  public TipOutcome {
    if (tipId == null || tipId.isBlank()) {
      throw new IllegalArgumentException("tipId must not be blank");
    }
    if (status == null || status.isBlank()) {
      throw new IllegalArgumentException("status must not be blank");
    }
  }
}
