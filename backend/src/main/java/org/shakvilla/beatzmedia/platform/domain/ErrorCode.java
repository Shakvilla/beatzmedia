package org.shakvilla.beatzmedia.platform.domain;

/**
 * Canonical error codes for the API error envelope. These are stable SCREAMING_SNAKE_CASE strings
 * that tests and clients can assert on. Conventions §4.
 */
public enum ErrorCode {
  VALIDATION,
  NOT_FOUND,
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
  INTERNAL
}
