package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port: the trust &amp; safety actions on a risk signal (LLFR-ADMIN-07.1), backing {@code POST
 * /v1/admin/risk/:id/{review|clear|ban}}. Each action loads the signal, applies the guarded domain
 * transition (409 {@code ILLEGAL_TRANSITION} on a non-{@code open} signal), and appends exactly one
 * {@code AuditEntry} (INV-10). {@code ban} additionally bans the subject account via the identity
 * port (sets it {@code banned}; existing tokens expire — stateless JWT, OQ-3). Auth: moderator /
 * super-admin. Admin ADD §4.1.
 */
public interface RiskActions {

  /** Audited acknowledgment; the signal stays {@code open} (409 if not open). */
  RiskSignalView review(String actorId, String signalId);

  /** {@code open → cleared} (409 if not open). */
  RiskSignalView clear(String actorId, String signalId);

  /**
   * {@code open → banned} + bans the subject account. {@code reason} is required (validated {@code
   * @NotBlank} at the REST boundary → 422). 409 if the signal is not open; 404 if the signal's
   * subject is not a resolvable account.
   */
  RiskSignalView ban(String actorId, String signalId, String reason);
}
