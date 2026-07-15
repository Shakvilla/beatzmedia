package org.shakvilla.beatzmedia.commerce.application.service;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.commerce.application.port.in.Checkout;
import org.shakvilla.beatzmedia.commerce.application.port.in.CheckoutResult;
import org.shakvilla.beatzmedia.commerce.application.port.out.CartRepository;
import org.shakvilla.beatzmedia.commerce.application.port.out.CatalogExpansionReader;
import org.shakvilla.beatzmedia.commerce.application.port.out.ChargeGateway;
import org.shakvilla.beatzmedia.commerce.application.port.out.OrderRefGenerator;
import org.shakvilla.beatzmedia.commerce.application.port.out.OrderRepository;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricingService;
import org.shakvilla.beatzmedia.commerce.domain.Cart;
import org.shakvilla.beatzmedia.commerce.domain.CartEmptyException;
import org.shakvilla.beatzmedia.commerce.domain.CartItem;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.ChargeAmountExceededException;
import org.shakvilla.beatzmedia.commerce.domain.CheckoutKindUnsupportedException;
import org.shakvilla.beatzmedia.commerce.domain.IdempotencyConflictException;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OrderLine;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Application service for {@code POST /v1/checkout} ({@link Checkout}, LLFR-COMMERCE-02.1). Commerce
 * ADD §8.
 *
 * <p>Flow:
 *
 * <ol>
 *   <li>Idempotency short-circuit: an existing order for {@code (account, idempotencyKey)} is
 *       returned verbatim (same order + intent, no second charge). Commerce ADD §9.2.
 *   <li>Load the caller's OWN cart (409 {@code CART_EMPTY} if empty/absent) — the cart is keyed by
 *       {@code account}, so there is no cross-account checkout (WU-PAY-1 authz carryover).
 *   <li><strong>G3 gate:</strong> reject any line whose authoritative pricing module is not live yet
 *       ({@code episode}/{@code season-pass}/{@code ticket}/{@code store}) — 409
 *       {@code CHECKOUT_KIND_UNSUPPORTED}. Only {@code track}/{@code album}/{@code album-rest} (priced
 *       from catalog) proceed. (ADR-23).
 *   <li><strong>G1 authoritative re-pricing:</strong> re-resolve every unit price server-side via
 *       {@link PricingService} — the cart-stored price is NEVER trusted (INV-11).
 *   <li>Compute totals, enforce the {@code PlatformSettings} charge ceiling (422
 *       {@code CHARGE_AMOUNT_EXCEEDED} — bounded, never an unmapped 500).
 *   <li>Persist a {@code pending} {@link Order} with a collision-safe reference; initiate the charge
 *       via {@link ChargeGateway} (idempotency key forwarded to payments); attach the intent id.
 *   <li>Append exactly one {@link AuditEntry} (INV-10, actor = the fan). No ownership is granted here
 *       — that happens only on settlement (INV-1).
 * </ol>
 */
@ApplicationScoped
public class CheckoutService implements Checkout {

  private final CartRepository cartRepository;
  private final OrderRepository orderRepository;
  private final PricingService pricingService;
  private final CatalogExpansionReader expansionReader;
  private final OrderRefGenerator orderRefGenerator;
  private final ChargeGateway chargeGateway;
  private final PlatformSettingsProvider settingsProvider;
  private final AuditWriter auditWriter;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public CheckoutService(
      CartRepository cartRepository,
      OrderRepository orderRepository,
      PricingService pricingService,
      CatalogExpansionReader expansionReader,
      OrderRefGenerator orderRefGenerator,
      ChargeGateway chargeGateway,
      PlatformSettingsProvider settingsProvider,
      AuditWriter auditWriter,
      IdGenerator ids,
      Clock clock) {
    this.cartRepository = cartRepository;
    this.orderRepository = orderRepository;
    this.pricingService = pricingService;
    this.expansionReader = expansionReader;
    this.orderRefGenerator = orderRefGenerator;
    this.chargeGateway = chargeGateway;
    this.settingsProvider = settingsProvider;
    this.auditWriter = auditWriter;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  @Transactional
  public CheckoutResult checkout(AccountId account, String idempotencyKey, String paymentMethodId) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ValidationException("Idempotency-Key is required", "Idempotency-Key");
    }
    if (paymentMethodId == null || paymentMethodId.isBlank()) {
      throw new ValidationException("paymentMethodId is required", "paymentMethodId");
    }

    // 2. Load the caller's OWN cart (no cross-account checkout — cart is keyed by account).
    Cart cart =
        cartRepository.findByAccount(account).orElseThrow(CartEmptyException::new);
    if (cart.isEmpty()) {
      throw new CartEmptyException();
    }

    // 1. Idempotency (api-and-contract §5.2). The request hash covers the idempotency-relevant fields:
    // the cart contents (each line's kind:refId:qty, order-independent) and the paymentMethodId. On a
    // key match: SAME hash → true idempotent replay (return the stored order, no second charge);
    // DIFFERENT hash → 409 IDEMPOTENCY_KEY_CONFLICT (a client bug reusing a key for a different request
    // — never silently return the stale order or re-charge). Mirrors payments' InitiateChargeService.
    String requestHash = requestHash(cart, paymentMethodId);
    Order existing =
        orderRepository.findByAccountAndIdempotencyKey(account, idempotencyKey).orElse(null);
    if (existing != null) {
      if (!requestHash.equals(existing.getRequestHash())) {
        throw new IdempotencyConflictException(
            "Idempotency-Key already used with a different checkout request");
      }
      return toResult(existing);
    }

    PlatformSettings settings = settingsProvider.current();
    Currency currency = settings.defaultCurrency();

    // 3 + 4. Gate unsupported kinds (G3) and re-price authoritatively server-side (G1).
    List<OrderLine> lines = new ArrayList<>(cart.getItems().size());
    for (CartItem item : cart.getItems()) {
      CartItemKind kind = item.getKind();
      gateKind(kind);
      if (kind == CartItemKind.album_rest) {
        // F2: album-rest ("buy the rest") is priced ownership-aware — the SUM of the caller's
        // not-yet-owned, for-sale album tracks at each track's individual price, with NO bundle
        // discount (the 24% is a full-album-only, authoring-time concept). Never the album list price.
        lines.add(albumRestLine(account, item, currency));
        continue;
      }
      // Authoritative server-side re-price — the cart-stored price is NEVER trusted (INV-11 / G1).
      PricedItem priced = pricingService.priceFor(kind, item.getRefId(), item.getMetadata());
      lines.add(
          new OrderLine(
              ids.newId(),
              kind,
              item.getRefId(),
              priced.title(),
              priced.subtitle(),
              priced.image(),
              priced.unitPrice(),
              item.getQty()));
    }

    // 5. Build the order (totals computed from re-priced lines) + enforce the charge ceiling.
    Money serviceFee = Money.ofMinor(settings.serviceFeeMinor(), currency);
    String reference =
        orderRefGenerator.nextReference(clock.now().atZone(ZoneOffset.UTC).getYear());
    Order order =
        Order.create(
            new OrderId(ids.newId()), account, reference, lines, serviceFee, currency, clock.now());

    long maxCharge = settings.maxChargeMinor();
    if (order.getTotal().minor() > maxCharge) {
      throw new ChargeAmountExceededException(order.getTotal().minor(), maxCharge);
    }

    order.bindIdempotency(idempotencyKey, requestHash);
    orderRepository.save(order);

    // 6. Initiate the charge for the caller's OWN order total (idempotency key forwarded to payments).
    ChargeGateway.ChargeResult charge =
        chargeGateway.initiateCharge(
            account, reference, order.getTotal(), paymentMethodId, idempotencyKey);
    order.attachPaymentIntent(charge.paymentIntentId());
    orderRepository.save(order);

    // 7. Audit exactly once (INV-10, actor = the fan).
    audit(account, order, "CHECKOUT");

    return new CheckoutResult(
        order.getId().value(), reference, charge.paymentIntentId(), order.getStatus().wireValue());
  }

  /**
   * G3 gate: only {@code track}/{@code album}/{@code album-rest} have authoritative catalog pricing at
   * this WU's scope. {@code episode}/{@code season-pass}/{@code ticket}/{@code store} are priced from
   * client-supplied metadata by the interim adapter (// G2), so charging real money on them would be a
   * spoofing vector — reject until the owning module (WU-POD-1/EVT-1/STO-1) ships a real price port.
   */
  private void gateKind(CartItemKind kind) {
    switch (kind) {
      case track, album, album_rest -> {
        // authoritative catalog pricing — allowed
      }
      case episode, season_pass, ticket, store ->
          throw new CheckoutKindUnsupportedException(kind.wireValue());
    }
  }

  /**
   * Build the {@code album-rest} order line ownership-aware (F2). The unit price is the SUM of the
   * caller's remaining (not-yet-owned) for-sale album tracks at their individual prices — no bundle
   * discount, never the album list price. On settlement, {@code GrantOwnershipService} grants exactly
   * those remaining tracks (album-rest expansion returns only for-sale tracks, then the per-track
   * already-owned guard filters any owned since checkout).
   *
   * <p>If the caller already owns every for-sale track, there is nothing to buy — reject with 404
   * {@link PriceUnavailableException} rather than charging ₵0 / creating an empty order.
   */
  private OrderLine albumRestLine(AccountId account, CartItem item, Currency currency) {
    List<CatalogExpansionReader.PurchasableTrack> remaining =
        expansionReader.remainingForSaleTracks(account, item.getRefId());
    if (remaining.isEmpty()) {
      throw new PriceUnavailableException("album-rest", item.getRefId());
    }
    long totalMinor = remaining.stream().mapToLong(CatalogExpansionReader.PurchasableTrack::priceMinor).sum();
    // Title/subtitle/image come from the interim priced metadata (display only); the PRICE is the
    // authoritative server-computed sum above, never the client/cart value (G1/INV-11).
    PricedItem display = pricingService.priceFor(CartItemKind.album, item.getRefId(), item.getMetadata());
    return new OrderLine(
        ids.newId(),
        CartItemKind.album_rest,
        item.getRefId(),
        display.title(),
        display.subtitle(),
        display.image(),
        Money.ofMinor(totalMinor, currency),
        1);
  }

  private CheckoutResult toResult(Order order) {
    return new CheckoutResult(
        order.getId().value(),
        order.getReference(),
        order.getPaymentIntentId(),
        order.getStatus().wireValue());
  }

  private void audit(AccountId actor, Order order, String action) {
    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            actor.value(),
            action,
            "Order",
            order.getId().value(),
            AuditType.FINANCE,
            null,
            clock.now()));
  }

  /**
   * SHA-256 over the idempotency-relevant fields of the checkout request (api-and-contract §5.2): the
   * cart contents — each line's {@code kind:refId:qty}, sorted so ordering is irrelevant — plus the
   * {@code paymentMethodId}. Two checkouts under the same key must carry the same hash to be treated
   * as the same operation; a different hash (e.g. a changed cart or a different rail) is a 409 conflict.
   * The re-priced amount is intentionally NOT part of the hash: server-side re-pricing (G1) means the
   * client never supplies it, and a legitimate replay of the same cart must not spuriously conflict if
   * a background price change occurred — the cart contents + method fully identify the intended request.
   */
  private static String requestHash(Cart cart, String paymentMethodId) {
    String canonical =
        cart.getItems().stream()
                .map(i -> i.getKind().wireValue() + ":" + i.getRefId() + ":" + i.getQty())
                .sorted()
                .collect(java.util.stream.Collectors.joining("|"))
            + "#"
            + paymentMethodId;
    try {
      byte[] hash =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(hash);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
