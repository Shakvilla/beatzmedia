package org.shakvilla.beatzmedia.media.adapter.out.integration;

import java.io.IOException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.media.application.port.out.ArtworkProcessorPort;
import org.shakvilla.beatzmedia.media.application.port.out.ObjectStorePort;
import org.shakvilla.beatzmedia.media.application.service.MagicByteValidator;
import org.shakvilla.beatzmedia.media.domain.ImageFormat;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;


/**
 * Artwork processor — validates image format by magic bytes and copies the original as the
 * primary delivery variant. v1 ships without resizing; the port interface allows richer variants
 * to be added without touching consumers. ADD §5.2.
 */
@ApplicationScoped
public class ImageArtworkProcessorAdapter implements ArtworkProcessorPort {

  private static final int MAGIC_PROBE = 8;

  private final ObjectStorePort objectStore;
  private final MagicByteValidator validator;

  @Inject
  public ImageArtworkProcessorAdapter(
      ObjectStorePort objectStore,
      MagicByteValidator validator) {
    this.objectStore = objectStore;
    this.validator = validator;
  }

  @Override
  public ImageFormat detectFormat(ObjectKey original) {
    byte[] header = downloadHeader(original, MAGIC_PROBE);
    return validator.detectImageFormat(header);
  }

  @Override
  public ObjectKey processVariants(ObjectKey original, MediaAssetId id) {
    ImageFormat fmt = detectFormat(original);
    String ext = fmt == ImageFormat.PNG ? "png" : "jpg";
    String artKey = "delivery/" + id.value() + "/art/cover-1024." + ext;
    String contentType = fmt == ImageFormat.PNG ? "image/png" : "image/jpeg";

    // Read from the asset's recorded store and write to the currently selected delivery store.
    ObjectStorePort.StoredObject source = objectStore.open(original);
    try (java.io.InputStream resp = source.body()) {
      return objectStore.putDelivery(id, artKey, resp, contentType);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to process artwork for " + id.value(), e);
    }
  }

  private byte[] downloadHeader(ObjectKey key, int maxBytes) {
    ObjectStorePort.StoredObject object = objectStore.open(key);
    try (java.io.InputStream resp = object.body()) {
      byte[] buf = new byte[maxBytes];
      int read = resp.readNBytes(buf, 0, maxBytes);
      return (read < maxBytes) ? java.util.Arrays.copyOf(buf, read) : buf;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read artwork header for " + key.key(), e);
    }
  }

}
