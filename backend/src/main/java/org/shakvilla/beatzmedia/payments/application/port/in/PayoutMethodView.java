package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;

/**
 * Read model for a payout method: {@code { id, label, detail, kind, isDefault }} — matches the
 * frontend {@code PayoutMethod} interface ({@code Frontend/src/lib/studio-payouts.ts}) verbatim.
 */
public record PayoutMethodView(
    String id, String label, String detail, String kind, boolean isDefault) {

  public static PayoutMethodView of(PayoutMethod m) {
    return new PayoutMethodView(
        m.getId().value(), m.getLabel(), m.getDetail(), m.getKind().name(), m.isDefault());
  }
}
