package org.shakvilla.beatzmedia.engagement.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

public class CheckpointSequenceConflictException extends ConflictException {
  public CheckpointSequenceConflictException(long sequence) {
    super(
        ErrorCode.CHECKPOINT_SEQUENCE_CONFLICT,
        "Checkpoint sequence " + sequence + " was already used with different content");
  }
}
