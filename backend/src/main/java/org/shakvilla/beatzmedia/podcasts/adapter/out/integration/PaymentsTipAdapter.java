package org.shakvilla.beatzmedia.podcasts.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.payments.application.port.in.IssueTip;
import org.shakvilla.beatzmedia.payments.application.port.in.TipView;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.podcasts.application.port.in.TipMethod;
import org.shakvilla.beatzmedia.podcasts.application.port.out.IssueTipUseCase;
import org.shakvilla.beatzmedia.podcasts.application.port.out.TipOutcome;

/**
 * Implements podcasts' {@link IssueTipUseCase} output port by calling payments' {@link IssueTip}
 * INPUT port in-process — podcasts NEVER reads or writes payments tables and NEVER re-implements the
 * split/ledger/idempotency. Podcasts ADD §5.2.
 *
 * <p>All the money machinery lives behind the input port: the tip becomes a {@code payment_intent}
 * with the recipient creator encoded into its {@code TipRef} (so settlement recovers the creator and
 * posts the 90/10 split, {@code PlatformSettings.tipFeePct}, OQ-2 default 10%, INV-4/INV-6), the
 * {@code idempotency_key} UNIQUE constraint makes a key replay a no-op single-effect (same key ⇒ same
 * tip, no double charge), and the INV-10 finance {@code AuditEntry} is appended on the {@code IssueTip}
 * path. No value moves until settlement (INV-1).
 *
 * <p>This adapter is a pure translation boundary: podcasts' framework-neutral {@link TipMethod}
 * (provider/kind/token) → payments' {@link PaymentMethodRef}, {@link AccountId} → payments'
 * {@code AccountId}, and {@code String idempotencyKey} → payments' {@link IdempotencyKey}. An unknown
 * provider/kind is a mapped {@code VALIDATION} 4xx, never an unmapped 500.
 */
@ApplicationScoped
public class PaymentsTipAdapter implements IssueTipUseCase {

  private final IssueTip issueTip;

  @Inject
  public PaymentsTipAdapter(IssueTip issueTip) {
    this.issueTip = issueTip;
  }

  @Override
  public TipOutcome issueTip(
      AccountId fan, AccountId creator, Money amount, TipMethod method, String idempotencyKey) {

    PaymentMethodRef ref =
        new PaymentMethodRef(
            parseProvider(method.provider()), parseKind(method.kind()), method.token());

    TipView view =
        issueTip.tip(
            new org.shakvilla.beatzmedia.payments.domain.AccountId(fan.value()),
            new org.shakvilla.beatzmedia.payments.domain.AccountId(creator.value()),
            amount,
            ref,
            new IdempotencyKey(idempotencyKey));

    return new TipOutcome(view.intentId(), view.status());
  }

  private static Provider parseProvider(String value) {
    try {
      return Provider.fromWire(value);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("unsupported provider: " + value, "provider");
    }
  }

  private static MethodKind parseKind(String value) {
    try {
      return MethodKind.fromWire(value);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("unsupported methodKind: " + value, "methodKind");
    }
  }
}
