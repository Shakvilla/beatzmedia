package org.shakvilla.beatzmedia.admin.domain;

import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * User-list filter for {@code GET /admin/users?filter=}. Admin ADD §5.1 (LLFR-ADMIN-02.1).
 *
 * <p>{@code null} means "no filter" (all users) — there is no {@code ALL} enum constant; a null
 * filter and an explicit {@code ?filter=} value are handled identically by {@link #fromWireValue}.
 */
public enum UserFilter {
  FANS,
  ARTISTS,
  VERIFIED,
  SUSPENDED;

  /**
   * Parses the {@code ?filter=} query parameter. Blank/missing → {@code null} (no filter, matches
   * the same null-means-default convention as {@code AdminRange#fromWireValue}). An unrecognised
   * value throws the generic {@link ValidationException} (422 {@code VALIDATION}) rather than a
   * bespoke {@code INVALID_FILTER} code — same "reuse the generic code" convention already
   * established for reason-required fields (admin ADD WU-ADM-2 as-built notes).
   */
  public static UserFilter fromWireValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    for (UserFilter f : values()) {
      if (f.name().equalsIgnoreCase(value)) {
        return f;
      }
    }
    throw new ValidationException("Unknown user filter: " + value, "filter");
  }
}
