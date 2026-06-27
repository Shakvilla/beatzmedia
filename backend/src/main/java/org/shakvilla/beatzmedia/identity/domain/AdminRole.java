package org.shakvilla.beatzmedia.identity.domain;

/**
 * Roles assignable to admin-team members. Serialized as kebab-case strings for the API wire format
 * and for persistence (CHECK constraint uses these values). Identity ADD §3 / API-CONTRACT §14.
 *
 * <p>super-admin = all · finance = payouts/ledger/disputes · moderator = moderation/takedowns ·
 * editor = editorial · support = user lookup + read-only elsewhere.
 */
public enum AdminRole {
  SUPER_ADMIN("super-admin"),
  FINANCE("finance"),
  MODERATOR("moderator"),
  EDITOR("editor"),
  SUPPORT("support");

  /** The kebab-case wire value used in the API and persisted in the DB CHECK constraint. */
  private final String wireValue;

  AdminRole(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  /**
   * Parses a kebab-case wire value (e.g. {@code "super-admin"}) to an {@link AdminRole}. Throws
   * {@link InvalidRoleException} if unrecognised.
   */
  public static AdminRole fromWireValue(String value) {
    if (value == null) {
      throw new InvalidRoleException("Role must not be null");
    }
    for (AdminRole r : values()) {
      if (r.wireValue.equalsIgnoreCase(value)) {
        return r;
      }
    }
    throw new InvalidRoleException("Unknown admin role: " + value);
  }
}
