package org.shakvilla.beatzmedia.engagement.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

public class ListeningSessionNotFoundException extends DomainException {
  public ListeningSessionNotFoundException() {
    super(ErrorCode.SESSION_NOT_FOUND, "Listening session not found");
  }
}
