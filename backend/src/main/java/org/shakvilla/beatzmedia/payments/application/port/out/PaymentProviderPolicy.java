package org.shakvilla.beatzmedia.payments.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.payments.domain.Provider;

/**
 * Outbound port: which payment rails the platform will currently accept charges on (GAP-13).
 *
 * <p><strong>Reads are fail-closed, and that is the entire point of this port existing.</strong>
 * {@code FeatureFlags.isEnabled} defaults an unknown key to {@code true} — "fail-open for
 * non-security features", which is right for {@code PODCASTS} and wrong for a payment rail. Reusing
 * it here would mean a provider whose row was missing kept taking money on a rail the operator
 * believed was switched off, with nothing to indicate it.
 *
 * <p>The counter-risk — a failed migration silently declining every payment — is handled at boot
 * instead: {@code PaymentProviderFlagsCheck} refuses to start the application when a provider row is
 * absent. Between the two, both failure modes become "does not start", which is the only outcome an
 * operator finds out about immediately rather than through a customer complaint.
 *
 * <p><strong>Charges only.</strong> {@link Provider} is used for payouts as well, and this port is
 * deliberately not consulted there: disabling a rail must not strand balances a creator has already
 * earned. "Stop accepting payments on this rail" and "stop paying creators out over it" are
 * different operator intentions, and collapsing them into one flag would make the safer one
 * unavailable. A payout-side policy can be added alongside this one when it is actually wanted.
 */
public interface PaymentProviderPolicy {

  /**
   * Whether new charges may be routed to this rail.
   *
   * @return {@code false} when the rail is disabled <em>or</em> when its flag cannot be read at all
   */
  boolean isEnabledForCharges(Provider provider);

  /** Every rail currently accepting charges. Drives the fan-facing method picker. */
  List<Provider> enabledForCharges();
}
