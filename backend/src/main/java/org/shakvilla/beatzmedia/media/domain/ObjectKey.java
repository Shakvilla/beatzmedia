package org.shakvilla.beatzmedia.media.domain;

/**
 * Fully-qualified S3/MinIO storage location: bucket + key. Framework-free value object. ADD §3.
 */
public record ObjectKey(String bucket, String key) {

  /** Canonical string used for persistence (bucket|key). */
  public String toStorageString() {
    return bucket + "|" + key;
  }

  /** Parse a stored "bucket|key" string. */
  public static ObjectKey fromStorageString(String stored) {
    int pipe = stored.indexOf('|');
    if (pipe < 1) {
      throw new IllegalArgumentException("Invalid ObjectKey storage string: " + stored);
    }
    return new ObjectKey(stored.substring(0, pipe), stored.substring(pipe + 1));
  }
}
