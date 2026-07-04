package org.shakvilla.beatzmedia.platform.domain;

/**
 * Canonical error codes for the API error envelope. These are stable SCREAMING_SNAKE_CASE strings
 * that tests and clients can assert on. Conventions §4.
 *
 * <p>Identity codes added for WU-IDN-1 (deliberate platform-kernel extension — see ADD §9):
 * EMAIL_TAKEN (409), INVALID_CREDENTIALS (401), WEAK_PASSWORD (422), ACCOUNT_SUSPENDED (403).
 * The wire {@code code} string equals the enum constant name exactly.
 */
public enum ErrorCode {
  VALIDATION,
  NOT_FOUND,
  METHOD_NOT_ALLOWED,
  UNAUTHENTICATED,
  UNAUTHORIZED,
  CONFLICT,
  ILLEGAL_TRANSITION,
  RATE_LIMITED,
  FEATURE_DISABLED,
  MAINTENANCE,
  PAYLOAD_TOO_LARGE,
  UNSUPPORTED_FORMAT,
  FILE_REJECTED,
  INTERNAL,
  // ---- Identity codes (WU-IDN-1) ----
  EMAIL_TAKEN,
  INVALID_CREDENTIALS,
  WEAK_PASSWORD,
  ACCOUNT_SUSPENDED,
  // ---- Identity codes (WU-IDN-4) ----
  INVALID_ROLE,
  LAST_SUPER_ADMIN,
  // ---- Identity codes (WU-IDN-2) ----
  SOCIAL_TOKEN_INVALID,
  // ---- Catalog codes (WU-CAT-1) ----
  ARTIST_NOT_FOUND,
  ALBUM_NOT_FOUND,
  TRACK_NOT_FOUND,
  LYRICS_NOT_FOUND,
  PLAYLIST_NOT_FOUND,
  // ---- Catalog codes (WU-CAT-2) ----
  MISSING_QUERY,
  // ---- Catalog codes (WU-CAT-3) ----
  TRACK_COUNT_INVALID,
  SPLIT_OVER_100,
  RELEASE_LIVE,
  RELEASE_NOT_FOUND,
  // ---- Payments codes (WU-PAY-1) ----
  MISSING_IDEMPOTENCY_KEY,
  IDEMPOTENCY_KEY_CONFLICT,
  PAYMENT_INTENT_NOT_FOUND,
  PROVIDER_ERROR,
  // ---- Payments codes (WU-PAY-4) — payouts/withdrawals/KYC ----
  BELOW_MIN_PAYOUT,
  INSUFFICIENT_BALANCE,
  KYC_REQUIRED,
  KYC_BLOCKED,
  PAYOUT_METHOD_IN_USE,
  PAYOUT_METHOD_NOT_FOUND,
  // ---- Commerce codes (WU-COM-1) ----
  ALREADY_OWNED,
  NOT_STACKABLE,
  // ---- Commerce codes (WU-COM-2) ----
  CART_EMPTY,
  CHECKOUT_KIND_UNSUPPORTED,
  CHARGE_AMOUNT_EXCEEDED,
  // ---- Playback codes (WU-PLY-1) ----
  MEDIA_UNAVAILABLE
}
