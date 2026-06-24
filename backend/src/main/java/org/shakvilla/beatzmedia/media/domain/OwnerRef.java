package org.shakvilla.beatzmedia.media.domain;

/**
 * Opaque back-reference to the consuming module's entity (e.g. "catalog:track-id"). Stored as a
 * single string "{module}:{entityId}" — no cross-module foreign key. ADD §3.
 */
public record OwnerRef(String module, String entityId) {

  /** Canonical string representation used for storage and idempotency keying. */
  public String toStorageString() {
    return module + ":" + entityId;
  }

  /** Parse a stored "{module}:{entityId}" string back to a value object. */
  public static OwnerRef fromStorageString(String stored) {
    int colon = stored.indexOf(':');
    if (colon < 1) {
      throw new IllegalArgumentException("Invalid OwnerRef storage string: " + stored);
    }
    return new OwnerRef(stored.substring(0, colon), stored.substring(colon + 1));
  }
}
