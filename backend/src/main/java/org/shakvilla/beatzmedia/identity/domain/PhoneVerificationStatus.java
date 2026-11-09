package org.shakvilla.beatzmedia.identity.domain;

/** Lifecycle states for a one-time phone verification challenge. */
public enum PhoneVerificationStatus {
  pending,
  sent,
  consumed,
  failed,
  expired
}
