package org.shakvilla.beatzmedia.platform.domain;

/**
 * Uniform error value object matching the API envelope {@code { error: { code, message, field? } }}.
 * Conventions §4 / API-CONTRACT.md.
 */
public record ApiError(String code, String message, String field) {

  /** Construct an error without a field pointer (general errors). */
  public static ApiError of(ErrorCode code, String message) {
    return new ApiError(code.name(), message, null);
  }

  /** Construct an error with a specific field pointer (validation errors). */
  public static ApiError of(ErrorCode code, String message, String field) {
    return new ApiError(code.name(), message, field);
  }
}
