package org.shakvilla.beatzmedia.media.application.port.in;

import java.time.Duration;

import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.SignedUrl;

/**
 * Input port: issue a signed, time-boxed delivery URL. INV-3 enforcement point — FULL is only
 * produced when the caller explicitly passes {@link DeliveryVariant#FULL} (ownership asserted
 * by the caller); otherwise PREVIEW is served. LLFR-MEDIA-01.3 / ADD §4.1.
 */
public interface IssueDeliveryUrlUseCase {

  /**
   * Presign the delivery key for the given asset.
   *
   * @param assetId  the asset
   * @param variant  FULL (caller has confirmed ownership) or PREVIEW (non-owner path)
   * @param ttl      URL time-to-live
   * @return a {@link SignedUrl} with {@code url} and {@code expiresAt}
   */
  SignedUrl issueSignedUrl(MediaAssetId assetId, DeliveryVariant variant, Duration ttl);
}
