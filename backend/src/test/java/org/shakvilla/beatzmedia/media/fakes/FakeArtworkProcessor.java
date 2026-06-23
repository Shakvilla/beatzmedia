package org.shakvilla.beatzmedia.media.fakes;

import org.shakvilla.beatzmedia.media.application.port.out.ArtworkProcessorPort;
import org.shakvilla.beatzmedia.media.domain.ImageFormat;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

/** Fake {@link ArtworkProcessorPort} for unit tests. Returns a delivery-bucket key. */
public class FakeArtworkProcessor implements ArtworkProcessorPort {

  /** Bucket name the fake always uses — asserts that tests prove delivery bucket is used. */
  public static final String DELIVERY_BUCKET = "test-delivery";

  @Override
  public ImageFormat detectFormat(ObjectKey original) {
    return ImageFormat.JPG;
  }

  @Override
  public ObjectKey processVariants(ObjectKey original, MediaAssetId id) {
    String artKey = "delivery/" + id.value() + "/art/cover-1024.jpg";
    return new ObjectKey(DELIVERY_BUCKET, artKey);
  }
}
