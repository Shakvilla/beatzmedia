package org.shakvilla.beatzmedia.commerce.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.commerce.application.port.out.ChargeGateway;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * In-memory fake {@link ChargeGateway} recording each initiated charge so tests can assert the amount
 * charged (proving G1 — the server charges its own re-priced total, never the client's) and the charge
 * count (proving idempotency — exactly one charge per key).
 */
public class FakeChargeGateway implements ChargeGateway {

  private final List<Charge> charges = new ArrayList<>();
  private int seq = 0;
  private String checkoutUrl = null;

  public record Charge(
      String actor, String orderReference, long amountMinor, String paymentMethodId, String key) {}

  /** Simulate a card charge that returns a hosted-checkout redirect URL (WU-COM-4). */
  public FakeChargeGateway withCheckoutUrl(String checkoutUrl) {
    this.checkoutUrl = checkoutUrl;
    return this;
  }

  @Override
  public ChargeResult initiateCharge(
      AccountId actor,
      String orderReference,
      Money amount,
      String paymentMethodId,
      String idempotencyKey) {
    charges.add(
        new Charge(
            actor.value(), orderReference, amount.minor(), paymentMethodId, idempotencyKey));
    return new ChargeResult("intent-" + (++seq), "pending", checkoutUrl);
  }

  /** All recorded charges. */
  public List<Charge> charges() {
    return List.copyOf(charges);
  }

  /** The most recent charge (fails if none). */
  public Charge last() {
    return charges.get(charges.size() - 1);
  }

  public int count() {
    return charges.size();
  }
}
