package org.shakvilla.beatzmedia.payments.it;

import static jakarta.enterprise.event.TransactionPhase.AFTER_SUCCESS;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.shakvilla.beatzmedia.payments.domain.PaymentFailed;
import org.shakvilla.beatzmedia.payments.domain.PaymentSettled;

/**
 * Test observer that captures the {@code PaymentSettled}/{@code PaymentFailed} domain events fired
 * AFTER_SUCCESS, so integration tests can assert exactly-once emission (LLFR-PAYMENTS-01.2/01.3)
 * without a read endpoint.
 */
@ApplicationScoped
public class PaymentEventRecorder {

  private final List<PaymentSettled> settled = new CopyOnWriteArrayList<>();
  private final List<PaymentFailed> failed = new CopyOnWriteArrayList<>();

  void onSettled(@Observes(during = AFTER_SUCCESS) PaymentSettled event) {
    settled.add(event);
  }

  void onFailed(@Observes(during = AFTER_SUCCESS) PaymentFailed event) {
    failed.add(event);
  }

  public long settledCountFor(String intentId) {
    return settled.stream().filter(e -> e.intentId().equals(intentId)).count();
  }

  public long failedCountFor(String intentId) {
    return failed.stream().filter(e -> e.intentId().equals(intentId)).count();
  }

  public Optional<PaymentFailed> lastFailedFor(String intentId) {
    return failed.stream().filter(e -> e.intentId().equals(intentId)).reduce((a, b) -> b);
  }

  public void clear() {
    settled.clear();
    failed.clear();
  }
}
