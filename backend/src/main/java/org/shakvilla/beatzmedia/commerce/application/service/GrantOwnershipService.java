package org.shakvilla.beatzmedia.commerce.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.commerce.application.port.in.GrantOwnership;
import org.shakvilla.beatzmedia.commerce.application.port.out.CartRepository;
import org.shakvilla.beatzmedia.commerce.application.port.out.CatalogExpansionReader;
import org.shakvilla.beatzmedia.commerce.application.port.out.OrderRepository;
import org.shakvilla.beatzmedia.commerce.application.port.out.OwnershipRepository;
import org.shakvilla.beatzmedia.commerce.application.port.out.SaleLedgerPoster;
import org.shakvilla.beatzmedia.commerce.domain.IllegalOrderTransitionException;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OrderLine;
import org.shakvilla.beatzmedia.commerce.domain.OwnershipGrant;
import org.shakvilla.beatzmedia.commerce.domain.OwnershipGranted;
import org.shakvilla.beatzmedia.commerce.domain.SaleRecorded;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Application service for {@link GrantOwnership} (INV-1/INV-2/INV-4) — the ONLY code path that creates
 * an {@link OwnershipGrant}. Invoked solely by {@link PaymentSettledSubscriber} on a settled payment
 * (Commerce ADD §8). Runs in its OWN transaction ({@code REQUIRES_NEW}) so the exactly-once grant
 * claim can fail and roll THIS transaction back in isolation, mirroring the payments WU-PAY-3
 * {@code TipLedgerPoster} pattern.
 *
 * <p>Steps (all atomic in one transaction):
 *
 * <ol>
 *   <li>Resolve the order by its reference (locked FOR UPDATE) — no cross-module read; the reference
 *       travels on the {@code PaymentSettled} event.
 *   <li>Guard {@code pending → paid} (INV-1) — a {@code failed}/{@code refunded} order never grants;
 *       an already-{@code paid} order is a benign no-op.
 *   <li><strong>Take the exactly-once claim</strong> on {@code order_grant_posting} (PRIMARY KEY on
 *       order id). A re-delivered settlement (webhook replay + poll race) loses the claim and the
 *       whole fan-out is skipped — exactly one grant set per order (INV-1). The per-target
 *       unique-active grant indexes are the durable per-row backstop.
 *   <li>Expand every album/season line to its constituent track/episode ids (INV-2) and create one
 *       grant per unit.
 *   <li>Post the 70/30 sale split per creator (INV-4) via the payments ledger port.
 *   <li>Clear the cart, publish {@link OwnershipGranted}, and audit (INV-10).
 * </ol>
 */
@ApplicationScoped
public class GrantOwnershipService implements GrantOwnership {

  private static final Logger LOG = Logger.getLogger(GrantOwnershipService.class);

  private final OrderRepository orderRepository;
  private final OwnershipRepository ownershipRepository;
  private final CatalogExpansionReader expansionReader;
  private final SaleLedgerPoster saleLedgerPoster;
  private final CartRepository cartRepository;
  private final AuditWriter auditWriter;
  private final Event<OwnershipGranted> ownershipGrantedEvent;
  private final Event<SaleRecorded> saleRecordedEvent;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public GrantOwnershipService(
      OrderRepository orderRepository,
      OwnershipRepository ownershipRepository,
      CatalogExpansionReader expansionReader,
      SaleLedgerPoster saleLedgerPoster,
      CartRepository cartRepository,
      AuditWriter auditWriter,
      Event<OwnershipGranted> ownershipGrantedEvent,
      Event<SaleRecorded> saleRecordedEvent,
      IdGenerator ids,
      Clock clock) {
    this.orderRepository = orderRepository;
    this.ownershipRepository = ownershipRepository;
    this.expansionReader = expansionReader;
    this.saleLedgerPoster = saleLedgerPoster;
    this.cartRepository = cartRepository;
    this.auditWriter = auditWriter;
    this.ownershipGrantedEvent = ownershipGrantedEvent;
    this.saleRecordedEvent = saleRecordedEvent;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void grantForSettledOrder(String orderReference, String paymentIntentId, String provider) {
    Order order = orderRepository.findByReferenceForUpdate(orderReference).orElse(null);
    if (order == null) {
      // Unknown order ref (e.g. a tip settlement, or a foreign reference) — nothing to grant.
      LOG.debugf("no commerce order for settled reference %s; skipping grant", orderReference);
      return;
    }

    // INV-1: transition pending -> paid. An already-paid order is a benign no-op (sequential replay).
    // A failed/refunded order must NEVER become paid — the aggregate throws; we treat that anomalous
    // late-settlement as a logged no-op so the (AFTER_SUCCESS) settlement path never blows up AND no
    // ownership is ever granted off a non-pending order (INV-1). This is defence-in-depth: a
    // PaymentFailed and a PaymentSettled for the same intent should never both fire (the payments
    // state machine transitions once), but if they race, no value is granted here.
    boolean transitioned;
    try {
      transitioned = order.markPaid();
    } catch (IllegalOrderTransitionException e) {
      LOG.warnf(
          "settlement for order %s in status %s cannot grant (INV-1); ignoring",
          order.getReference(), order.getStatus().wireValue());
      return;
    }
    if (!transitioned) {
      // Already paid (sequential replay fast-path) — the claim below would also reject it.
      LOG.debugf("order %s already paid; skipping duplicate grant", order.getReference());
      return;
    }

    // Exactly-once claim BEFORE any grant/credit. A concurrent re-delivery for the same order fails
    // the PRIMARY KEY here (false) and the whole fan-out is skipped — one grant set per order (INV-1).
    if (!ownershipRepository.claimGrantPosting(order.getId())) {
      LOG.debugf("grant posting already claimed for order %s; skipping", order.getReference());
      return;
    }

    orderRepository.save(order); // persist paid status

    AccountId buyer = order.getAccountId();
    Currency currency = order.getTotal().currency();
    List<String> grantedTrackIds = new ArrayList<>();
    List<String> grantedEpisodeIds = new ArrayList<>();

    // Accumulate each creator's gross across ALL their lines so we post exactly ONE sale split per
    // creator (finding F1). A per-line posting keyed on paymentIntentId collides on the payments
    // ledger_posting PK for a ≥2-creator order; aggregating per creator + a per-creator-unique ref
    // (below) makes distinct-creator postings independent and same-creator lines sum into one txn.
    // LinkedHashMap keeps a deterministic posting order (stable for tests / trace).
    Map<String, Long> grossByCreator = new LinkedHashMap<>();

    for (OrderLine line : order.getLines()) {
      // INV-2: album/season-pass lines expand to every constituent track/episode id.
      List<String> trackIds = expansionReader.tracksToGrant(line.getKind(), line.getRefId());
      for (String trackId : trackIds) {
        if (ownershipRepository.existsActiveForTrack(buyer, trackId)) {
          continue; // already owns (e.g. bought a track then the whole album) — no duplicate
        }
        ownershipRepository.save(
            OwnershipGrant.forTrack(ids.newId(), buyer, trackId, order.getId(), clock.now()));
        grantedTrackIds.add(trackId);
      }

      // INV-4: attribute this line's gross (unitPrice × qty) to its recipient creator for the split.
      expansionReader
          .creatorOf(line.getKind(), line.getRefId())
          .ifPresent(
              creator ->
                  grossByCreator.merge(creator, line.lineTotal().minor(), Long::sum));
    }

    // Post ONE 70/30 sale split per creator (percentage from PlatformSettings inside payments), with a
    // per-creator-unique source ref so distinct creators never collide on the ledger_posting PK, and
    // each runs in its own REQUIRES_NEW transaction so a duplicate can't poison this grant txn (F1).
    // Alongside each split, fire ONE SaleRecorded per creator so analytics (WU-ANA-1) can roll up
    // settled sales attributed to that artist — analytics never reads a commerce/payments table.
    Instant settledAt = clock.now();
    for (Map.Entry<String, Long> e : grossByCreator.entrySet()) {
      saleLedgerPoster.postSaleSplit(
          provider,
          new AccountId(e.getKey()),
          Money.ofMinor(e.getValue(), currency),
          saleRef(paymentIntentId, e.getKey()));
      saleRecordedEvent.fire(
          new SaleRecorded(order.getId().value(), e.getKey(), e.getValue(), currency.name(), settledAt));
    }

    // Clear the caller's cart on a successful purchase (idempotent).
    cartRepository.deleteByAccount(buyer);

    audit(buyer, order);

    ownershipGrantedEvent.fire(
        new OwnershipGranted(
            order.getId().value(),
            buyer.value(),
            order.getReference(),
            List.copyOf(grantedTrackIds),
            List.copyOf(grantedEpisodeIds),
            clock.now()));
  }

  private void audit(AccountId buyer, Order order) {
    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            buyer.value(),
            "OWNERSHIP_GRANTED",
            "Order",
            order.getId().value(),
            AuditType.FINANCE,
            null,
            clock.now()));
  }

  /**
   * The per-creator-unique ledger source ref for a sale split (finding F1):
   * {@code paymentIntentId + ":" + creatorAccountId}. This makes each creator's posting a distinct
   * {@code (ref_type="intent", ref_id)} key in the payments {@code ledger_posting} PK, so a multi-
   * creator order posts one balanced split per creator with no PK collision. Re-delivery idempotency
   * is handled by commerce's order-level {@code order_grant_posting} claim, not by this ref.
   */
  private static String saleRef(String paymentIntentId, String creatorAccountId) {
    return paymentIntentId + ":" + creatorAccountId;
  }
}
