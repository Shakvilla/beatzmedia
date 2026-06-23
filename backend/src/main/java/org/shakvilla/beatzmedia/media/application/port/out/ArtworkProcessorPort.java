package org.shakvilla.beatzmedia.media.application.port.out;

import org.shakvilla.beatzmedia.media.domain.ImageFormat;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

/** Output port for artwork validation and variant generation. ADD §4.2. */
public interface ArtworkProcessorPort {

  /**
   * Detect the image format from the stored original using magic bytes.
   *
   * @param original the originals-bucket key of the uploaded image
   * @return the detected format
   */
  ImageFormat detectFormat(ObjectKey original);

  /**
   * Validate and emit delivery variants (e.g. cover-1024.jpg) to the delivery bucket.
   *
   * @param original the originals-bucket key
   * @param id       the asset id (used to compose delivery/art/ prefix)
   * @return the delivery-bucket key of the primary processed variant
   */
  ObjectKey processVariants(ObjectKey original, MediaAssetId id);
}
