package org.shakvilla.beatzmedia.catalog.domain;

/** Confirmation state for a revenue split entry. Catalog ADD §3. */
public enum SplitConfirmation {
  self, confirmed, pending, auto, declined
}
