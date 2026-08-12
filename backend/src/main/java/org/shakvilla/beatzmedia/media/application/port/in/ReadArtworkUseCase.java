package org.shakvilla.beatzmedia.media.application.port.in;

import org.shakvilla.beatzmedia.media.application.port.out.ObjectStorePort.StoredObject;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;

/**
 * Reads a processed artwork asset for public delivery.
 *
 * <p>Images are streamed through the app rather than presigned. {@code UrlSignerPort.presignGet}
 * produces a time-boxed URL, which is right for a 30-second audio preview and wrong for a cover
 * image: {@code podcast.image} is persisted once and rendered by every fan from then on, so a URL
 * that expires would turn every browse card into a broken image the moment the TTL passed.
 *
 * <p>Deliberately a thin seam. When images move to a CDN, the REST layer redirects instead of
 * streaming and the stored URLs never change.
 */
public interface ReadArtworkUseCase {

  /**
   * @throws org.shakvilla.beatzmedia.platform.domain.NotFoundException if the asset is unknown, is
   *     not artwork, or has not finished processing
   */
  StoredObject openArtwork(MediaAssetId assetId);
}
