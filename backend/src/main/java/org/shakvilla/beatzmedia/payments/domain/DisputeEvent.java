package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A single timeline entry on a {@link Dispute} (payments ADD §3). Records a human-readable {@code
 * text}, the {@code actor} who caused it (an admin account id, or {@code "provider"}/{@code "system"}
 * for provider/automated events), and the instant. Immutable value object; framework-free.
 */
public record DisputeEvent(String id, DisputeId disputeId, String text, String actor, Instant at) {

  public DisputeEvent {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(disputeId, "disputeId");
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("dispute event text must not be blank");
    }
    Objects.requireNonNull(at, "at");
  }

  public static DisputeEvent of(String id, DisputeId disputeId, String text, String actor, Instant at) {
    return new DisputeEvent(id, disputeId, text, actor, at);
  }
}
