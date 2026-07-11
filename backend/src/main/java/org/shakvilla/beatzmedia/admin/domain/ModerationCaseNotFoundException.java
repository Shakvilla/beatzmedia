package org.shakvilla.beatzmedia.admin.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a {@link ModerationCase} lookup by id finds nothing. Maps to HTTP 404 {@code
 * NOT_FOUND} via the shared {@code DomainExceptionMapper} — no admin-specific exception mapper
 * needed (mirrors {@code InvalidAdminRangeException}'s precedent of extending {@link
 * DomainException} directly). Admin ADD §9 / LLFR-ADMIN-04.1.
 */
public class ModerationCaseNotFoundException extends DomainException {

  public ModerationCaseNotFoundException(String id) {
    super(ErrorCode.NOT_FOUND, "Moderation case not found: " + id);
  }
}
