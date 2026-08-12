package org.shakvilla.beatzmedia.platform.domain;

/**
 * Feature flag keys. Each key controls a distinct platform capability that can be toggled at
 * runtime without a deployment. ADD §3.3 / LLFR-ADMIN-10.1.
 */
public enum FeatureKey {
  /** Artist account creation and upgrade path. */
  ARTIST_SIGNUPS,
  /** Podcast browsing and episode access. */
  PODCASTS,
  /** Event browsing and ticketing. */
  EVENTS,
  /** Tipping creators. */
  TIPPING,
  /** Fan-to-fan direct messaging (ships disabled per PRD §1.4). */
  FAN_MESSAGING,
  /**
   * Route real payment charges/payouts through the Redde PSP (WU-PAY-6/7, ADR-27). When enabled,
   * {@code PaymentGatewayRouter} dispatches to {@code ReddePaymentGateway}; when disabled (the
   * default), to the {@code SandboxPaymentGateway}. This is an operational payments toggle, NOT part
   * of the admin {@code PlatformSettings} surface — the five keys above are the ones surfaced by
   * {@code GET/PUT /v1/admin/settings}; this one is toggled out-of-band. Ships disabled and MUST be
   * seeded {@code false} (V966), because {@code FeatureFlagsAdapter.isEnabled} fails OPEN for a key
   * with no row and real Redde credentials are a deploy-secret human gate.
   */
  PSP_REDDE,

  // ---------------------------------------------------------------------------------------------
  // Payment rails (GAP-13). One key per Provider, surfaced by GET/PUT /v1/admin/settings as
  // `providers.*`. These differ from every key above in two ways that matter:
  //
  //   1. They are read FAIL-CLOSED, via PaymentProviderPolicy, not through FeatureFlags.isEnabled
  //      — which defaults an unknown key to TRUE. A missing row here must never mean "keep taking
  //      money on a rail the operator believes is off".
  //   2. Their absence is a boot failure (PaymentProviderFlagsCheck), so a migration that did not
  //      run surfaces at deploy rather than as silently declined checkouts.
  //
  // Naming matches payments' `Provider` enum exactly. The admin console used to say `momo` and
  // `vodafone`; `vodafone` in particular named a brand that stopped existing in 2023, while
  // checkout and payouts already said Telecel.
  // ---------------------------------------------------------------------------------------------
  /** MTN MoMo. */
  PROVIDER_MTN,
  /** Telecel Cash — formerly Vodafone Cash. */
  PROVIDER_TELECEL,
  /** AirtelTigo Money. */
  PROVIDER_AIRTELTIGO,
  /** Card. */
  PROVIDER_CARD,
  /** Bank transfer. */
  PROVIDER_BANK
}
