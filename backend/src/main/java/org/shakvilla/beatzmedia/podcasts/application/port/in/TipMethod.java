package org.shakvilla.beatzmedia.podcasts.application.port.in;

/**
 * The payment instrument a tip charges: the rail ({@code provider}, e.g. {@code mtn}), the
 * instrument {@code kind} (e.g. {@code momo}), and an opaque provider {@code token} (e.g. a MoMo
 * handle). Podcasts-owned, framework-free wire-neutral VO — the adapter translates it to payments'
 * {@code PaymentMethodRef}. The raw token is never logged. ADD §4.1 / §6.
 *
 * <p>The frontend tip modal ({@code SupportModal}) is MoMo-first; the client supplies the rail +
 * token, so no default rail is guessed server-side (a tip must charge a real instrument).
 */
public record TipMethod(String provider, String kind, String token) {

  public TipMethod {
    if (provider == null || provider.isBlank()) {
      throw new IllegalArgumentException("tip payment provider is required");
    }
    if (kind == null || kind.isBlank()) {
      throw new IllegalArgumentException("tip payment method kind is required");
    }
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("tip payment token is required");
    }
  }
}
