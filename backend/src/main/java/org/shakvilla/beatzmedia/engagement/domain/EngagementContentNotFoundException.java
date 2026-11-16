package org.shakvilla.beatzmedia.engagement.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

public class EngagementContentNotFoundException extends DomainException {
  public EngagementContentNotFoundException() {
    super(ErrorCode.CONTENT_NOT_FOUND, "Engagement content not found");
  }
}
