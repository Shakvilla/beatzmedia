package org.shakvilla.beatzmedia.commerce.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OwnershipGrant;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Output port: ownership-grant persistence (Commerce ADD §4.2). Reads/writes only the
 * {@code ownership_grant} + {@code order_grant_posting} tables.
 *
 * <p><strong>INV-1 exactly-once.</strong> {@link #claimGrantPosting(OrderId)} inserts the per-order
 * claim header (PRIMARY KEY on {@code order_id}); a concurrent re-delivered settlement for the same
 * order loses the claim and the whole grant fan-out is skipped, so ownership is granted exactly once
 * per order. Per-target the unique-active indexes ({@code ux_grant_account_track/episode}) are the
 * durable backstop against a duplicate grant.
 */
public interface OwnershipRepository {

  /**
   * Take the exactly-once claim to expand grants for a settled order by inserting the
   * {@code order_grant_posting} header. Returns {@code true} if THIS caller won the claim (first
   * delivery), {@code false} if a posting already exists (re-delivery — the caller must skip the
   * fan-out). Atomic; the claim and the grants commit in the same transaction (INV-1).
   */
  boolean claimGrantPosting(OrderId orderId);

  /** Persist a new ownership grant (INV-1/INV-2). */
  OwnershipGrant save(OwnershipGrant grant);

  boolean existsActiveForTrack(AccountId account, String trackId);

  /**
   * The account's ACTIVE grant for the track, if any — the grant itself, not just its existence,
   * because callers need the permission it carries ({@code downloadable}). Backs
   * {@code GetTrackDownloadPermission}. Empty when never bought or revoked (INV-9).
   *
   * <p>At most one active grant can exist per (account, track): the unique-active index
   * {@code ux_grant_account_track} is the durable guarantee.
   */
  Optional<OwnershipGrant> findActiveForTrack(AccountId account, String trackId);

  boolean existsActiveForEpisode(AccountId account, String episodeId);

  /**
   * Does ANY account currently hold an active grant for the given episode — an aggregate query, not
   * account-scoped. Backs {@code GetOwnedEpisodeIds#hasAnyOwner} (WU-STU-2 delete guard, OQ-8).
   */
  boolean existsAnyActiveForEpisode(String episodeId);

  /** All grants (active or revoked) whose {@code source_order_id} matches — used by refund (INV-9). */
  List<OwnershipGrant> findBySourceOrder(OrderId orderId);

  /** Persist the revocation ({@code revoked_at}) of a grant (INV-9). */
  OwnershipGrant update(OwnershipGrant grant);

  /** All track ids the account currently owns (active grants) — backs library's owned read. */
  List<String> activeTrackIds(AccountId account);

  /**
   * Of the given candidate episode ids, the subset the account currently owns (active grants).
   * Backs the podcasts module's {@code OwnershipReader} output port (WU-POD-1) via commerce's
   * {@code GetOwnedEpisodeIds} input port — a single batched query avoids N+1 when decorating an
   * episode list.
   */
  List<String> activeEpisodeIds(AccountId account, List<String> candidateEpisodeIds);

  /**
   * Of the given candidate track ids, the subset for which the account holds an ACTIVE grant that
   * also permits downloading ({@code downloadable = true}) — a single batched query, mirroring
   * {@link #activeEpisodeIds}. Backs {@code GetTrackDownloadPermission#downloadableTrackIds}: a
   * library collection load of N owned tracks must decorate all of them with one query, not N.
   */
  List<String> downloadableTrackIds(AccountId account, List<String> candidateTrackIds);
}
