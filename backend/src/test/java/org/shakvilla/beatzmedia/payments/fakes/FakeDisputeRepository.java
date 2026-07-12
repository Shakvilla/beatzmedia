package org.shakvilla.beatzmedia.payments.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.payments.application.port.out.DisputeRepository;
import org.shakvilla.beatzmedia.payments.domain.Dispute;
import org.shakvilla.beatzmedia.payments.domain.DisputeEvent;
import org.shakvilla.beatzmedia.payments.domain.DisputeId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.Refund;

/**
 * In-memory {@link DisputeRepository} fake for unit tests. Only the read paths exercised by the
 * finance-overview aggregation ({@link #findOpen}) are backed; the mutation/lock methods throw so a
 * test that accidentally depends on them fails loudly rather than silently.
 */
public class FakeDisputeRepository implements DisputeRepository {

  /** The open disputes {@link #findOpen} will return (most-recent-first ordering is the caller's). */
  public final List<Dispute> open = new ArrayList<>();

  @Override
  public List<Dispute> findOpen(int limit) {
    if (limit <= 0) {
      return List.of();
    }
    return open.stream().limit(limit).toList();
  }

  @Override
  public void lockForIdempotencyKey(IdempotencyKey key) {
    throw new UnsupportedOperationException("not needed for finance-overview tests");
  }

  @Override
  public Dispute saveDispute(Dispute dispute) {
    throw new UnsupportedOperationException("not needed for finance-overview tests");
  }

  @Override
  public Optional<Dispute> saveChargebackDispute(Dispute dispute, String providerCaseId) {
    throw new UnsupportedOperationException("not needed for finance-overview tests");
  }

  @Override
  public Optional<Dispute> findDispute(DisputeId id) {
    return open.stream().filter(d -> d.getId().equals(id)).findFirst();
  }

  @Override
  public Optional<Dispute> findDisputeForUpdate(DisputeId id) {
    return findDispute(id);
  }

  @Override
  public Optional<Dispute> findByProviderCase(String providerCaseId) {
    return Optional.empty();
  }

  @Override
  public List<DisputeEvent> timelineOf(DisputeId id) {
    return List.of();
  }

  @Override
  public DisputeEvent saveEvent(DisputeEvent event) {
    throw new UnsupportedOperationException("not needed for finance-overview tests");
  }

  @Override
  public Refund saveRefund(Refund refund, String clawbackTxnId) {
    throw new UnsupportedOperationException("not needed for finance-overview tests");
  }
}
