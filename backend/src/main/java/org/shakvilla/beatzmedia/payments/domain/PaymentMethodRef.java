package org.shakvilla.beatzmedia.payments.domain;

/**
 * Reference to the payment instrument a charge should use: which rail ({@link Provider}), the kind
 * of instrument ({@link MethodKind}), and an opaque provider token (e.g. a MoMo MSISDN handle or a
 * card token). The raw token is never logged. Payments ADD §4.1.
 */
public record PaymentMethodRef(Provider provider, MethodKind kind, String token) {

  public PaymentMethodRef {
    if (provider == null) {
      throw new IllegalArgumentException("provider must not be null");
    }
    if (kind == null) {
      throw new IllegalArgumentException("kind must not be null");
    }
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("payment method token must not be blank");
    }
  }
}
