package org.shakvilla.beatzmedia.media.fakes;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.shakvilla.beatzmedia.media.application.port.out.ObjectStorePort;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

/** In-memory fake for {@link ObjectStorePort} used in unit tests. */
public class FakeObjectStore implements ObjectStorePort {

  private final Map<String, byte[]> store = new HashMap<>();

  @Override
  public ObjectKey putOriginal(
      MediaKind kind, MediaAssetId id, InputStream body, String contentType) {
    String key = "originals/" + kind.name().toLowerCase() + "/" + id.value();
    storeBytes("test-originals", key, body);
    return new ObjectKey("test-originals", key);
  }

  @Override
  public ObjectKey putDelivery(
      MediaAssetId id, String relativeKey, InputStream body, String contentType) {
    storeBytes("test-delivery", relativeKey, body);
    return new ObjectKey("test-delivery", relativeKey);
  }

  @Override
  public boolean exists(ObjectKey key) {
    return store.containsKey(key.bucket() + "|" + key.key());
  }

  public boolean objectExists(String bucket, String key) {
    return store.containsKey(bucket + "|" + key);
  }

  private void storeBytes(String bucket, String key, InputStream body) {
    try {
      store.put(bucket + "|" + key, body.readAllBytes());
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
  }
}
