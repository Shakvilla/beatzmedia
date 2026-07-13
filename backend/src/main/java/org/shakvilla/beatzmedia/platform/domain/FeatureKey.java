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
  PSP_REDDE
}
