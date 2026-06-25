package org.shakvilla.beatzmedia.identity.domain;

/**
 * Lifecycle status of an {@link Account}. Stored as TEXT in the DB. Identity ADD §3 / PRD §3.2.
 *
 * <p>State machine: active ↔ pending ↔ suspended → banned (terminal). Suspended/banned accounts
 * cannot authenticate (INV — ACCOUNT_SUSPENDED).
 */
public enum AccountStatus {
  active,
  pending,
  suspended,
  banned
}
