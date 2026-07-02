package org.shakvilla.beatzmedia.payments.domain;

/**
 * Encodes a tip's recipient creator into the opaque {@link OrderRef} carried by a tip's backing
 * {@code payment_intent} (format {@code TIP:<creatorAccountId>}). This lets settlement — which is
 * driven by the provider webhook/poll and carries only the intent's {@code orderRef} + payer
 * {@code accountId} (see {@link PaymentSettled}) — recover the recipient creator and post the 90/10
 * tip split WITHOUT any cross-module read (the tip recipient is fully within payments' knowledge,
 * unlike a sale whose creator mapping is commerce's concern, WU-COM-2). Framework-free.
 *
 * <p>INV-1 is preserved: the split is posted only when the intent reaches {@code settled}; the
 * encoded ref is inert data until then.
 */
public final class TipRef {

  private static final String PREFIX = "TIP:";

  private TipRef() {}

  /** Build the opaque order-ref for a tip to {@code creator}. */
  public static OrderRef forCreator(AccountId creator) {
    if (creator == null) {
      throw new IllegalArgumentException("creator must not be null");
    }
    return new OrderRef(PREFIX + creator.value());
  }

  /** True if the given order-ref encodes a tip. */
  public static boolean isTip(String orderRef) {
    return orderRef != null && orderRef.startsWith(PREFIX) && orderRef.length() > PREFIX.length();
  }

  /**
   * Extract the recipient creator from a tip order-ref.
   *
   * @throws IllegalArgumentException if the ref does not encode a tip
   */
  public static AccountId creatorOf(String orderRef) {
    if (!isTip(orderRef)) {
      throw new IllegalArgumentException("not a tip order ref: " + orderRef);
    }
    return new AccountId(orderRef.substring(PREFIX.length()));
  }
}
