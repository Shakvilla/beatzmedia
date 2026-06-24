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
  FAN_MESSAGING
}
