package org.shakvilla.beatzmedia.studio.domain;

/**
 * Studio team member role — closed taxonomy verbatim from {@code TeamMember.role} in {@code
 * Frontend/src/lib/studio-data.ts}. Studio ADD §3.
 */
public enum TeamRole {
  OWNER("Owner"),
  MANAGER("Manager"),
  LABEL("Label"),
  INVITED("Invited");

  private final String wireValue;

  TeamRole(String wireValue) {
    this.wireValue = wireValue;
  }

  /** The exact wire string used by the frontend/API (e.g. {@code "Owner"}). */
  public String wireValue() {
    return wireValue;
  }

  /** {@code true} if {@code wireValue} is a known team role (exact, case-sensitive match). */
  public static boolean isValid(String wireValue) {
    if (wireValue == null) {
      return false;
    }
    for (TeamRole role : values()) {
      if (role.wireValue.equals(wireValue)) {
        return true;
      }
    }
    return false;
  }

  /** Parse the wire string back to the enum constant. */
  public static TeamRole fromWireValue(String wireValue) {
    for (TeamRole role : values()) {
      if (role.wireValue.equals(wireValue)) {
        return role;
      }
    }
    throw new IllegalArgumentException("Unknown team role: " + wireValue);
  }
}
