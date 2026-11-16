package org.shakvilla.beatzmedia.media.domain;

/**
 * Fully-qualified S3/MinIO storage location: bucket + key. Framework-free value object. ADD §3.
 */
public record ObjectKey(String store, String bucket, String key) {

  /** Legacy constructor; old persisted references are MinIO-backed. */
  public ObjectKey(String bucket, String key) {
    this("minio", bucket, key);
  }

  /** Canonical persistence format; legacy MinIO references keep their original two-part format. */
  public String toStorageString() {
    return store.equals("minio") ? bucket + "|" + key : store + "|" + bucket + "|" + key;
  }

  /** Parse a current or legacy stored location. */
  public static ObjectKey fromStorageString(String stored) {
    int pipe = stored.indexOf('|');
    if (pipe < 1) {
      throw new IllegalArgumentException("Invalid ObjectKey storage string: " + stored);
    }
    int secondPipe = stored.indexOf('|', pipe + 1);
    if (secondPipe < 0) {
      return new ObjectKey("minio", stored.substring(0, pipe), stored.substring(pipe + 1));
    }
    return new ObjectKey(
        stored.substring(0, pipe), stored.substring(pipe + 1, secondPipe),
        stored.substring(secondPipe + 1));
  }
}
