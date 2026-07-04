package org.shakvilla.beatzmedia.podcasts.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a fan tries to tip a show that does not accept tips — either the show's
 * {@code supportsTips} flag is false, or the show has no owning creator account to receive the tip
 * (no {@code creator_account_id}; studio authoring is WU-STU-2). Maps to HTTP 403 /
 * {@code TIPS_DISABLED}. Podcasts ADD §5.1 / §9. This is a distinct signal from the platform-wide
 * {@code TIPPING} feature flag being off ({@code FEATURE_DISABLED}).
 */
public class TipsDisabledException extends DomainException {

  public TipsDisabledException(String podcastId) {
    super(ErrorCode.TIPS_DISABLED, "Tipping is not available for podcast: " + podcastId);
  }
}
