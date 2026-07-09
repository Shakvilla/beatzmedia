package org.shakvilla.beatzmedia.commerce.fakes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.shakvilla.beatzmedia.commerce.application.port.out.OwnershipRepository;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OwnershipGrant;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * In-memory fake {@link OwnershipRepository}. The {@code order_grant_posting} claim is modelled by a
 * set of claimed order ids so a re-delivered settlement returns {@code false} (exactly-once), exactly
 * like the real {@code ON CONFLICT DO NOTHING} insert.
 */
public class FakeOwnershipRepository implements OwnershipRepository {

  private final List<OwnershipGrant> grants = new ArrayList<>();
  private final Set<String> claimedOrders = new HashSet<>();

  @Override
  public boolean claimGrantPosting(OrderId orderId) {
    return claimedOrders.add(orderId.value()); // false if already claimed (re-delivery)
  }

  /**
   * Model the outer settlement→grant transaction ROLLING BACK (finding B): undo the grant-posting
   * claim and remove every grant created for the order. Used by the atomicity test to reproduce what
   * the {@code @Transactional(REQUIRES_NEW)} container rollback of {@code grantForSettledOrder} does
   * to these tables when a non-duplicate split failure propagates out. NOTE: this does NOT touch
   * committed ledger splits — those are the separate REQUIRES_NEW transactions that already committed.
   */
  public void rollbackGrant(OrderId orderId) {
    claimedOrders.remove(orderId.value());
    grants.removeIf(g -> g.getSourceOrderId().value().equals(orderId.value()));
  }

  @Override
  public OwnershipGrant save(OwnershipGrant grant) {
    grants.add(grant);
    return grant;
  }

  @Override
  public boolean existsActiveForTrack(AccountId account, String trackId) {
    return grants.stream()
        .anyMatch(
            g ->
                g.isActive()
                    && g.getAccountId().value().equals(account.value())
                    && trackId.equals(g.getTrackId()));
  }

  @Override
  public boolean existsActiveForEpisode(AccountId account, String episodeId) {
    return grants.stream()
        .anyMatch(
            g ->
                g.isActive()
                    && g.getAccountId().value().equals(account.value())
                    && episodeId.equals(g.getEpisodeId()));
  }

  @Override
  public boolean existsAnyActiveForEpisode(String episodeId) {
    return grants.stream().anyMatch(g -> g.isActive() && episodeId.equals(g.getEpisodeId()));
  }

  @Override
  public List<OwnershipGrant> findBySourceOrder(OrderId orderId) {
    return grants.stream()
        .filter(g -> g.getSourceOrderId().value().equals(orderId.value()))
        .toList();
  }

  @Override
  public OwnershipGrant update(OwnershipGrant grant) {
    return grant; // grants list holds the same object references; revoke mutates in place
  }

  @Override
  public List<String> activeTrackIds(AccountId account) {
    return grants.stream()
        .filter(g -> g.isActive() && g.getAccountId().value().equals(account.value()))
        .map(OwnershipGrant::getTrackId)
        .filter(t -> t != null)
        .toList();
  }

  @Override
  public List<String> activeEpisodeIds(AccountId account, List<String> candidateEpisodeIds) {
    if (candidateEpisodeIds == null || candidateEpisodeIds.isEmpty()) {
      return List.of();
    }
    Set<String> candidates = new HashSet<>(candidateEpisodeIds);
    return grants.stream()
        .filter(
            g ->
                g.isActive()
                    && g.getAccountId().value().equals(account.value())
                    && g.getEpisodeId() != null
                    && candidates.contains(g.getEpisodeId()))
        .map(OwnershipGrant::getEpisodeId)
        .toList();
  }

  /** Test helper: all grants (active or revoked). */
  public List<OwnershipGrant> all() {
    return List.copyOf(grants);
  }

  /** Test helper: count of active track grants for an account. */
  public long activeTrackCount(AccountId account) {
    return activeTrackIds(account).size();
  }
}
