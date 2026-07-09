package org.shakvilla.beatzmedia.studio.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown by studio's own coarse pre-validation of the uploaded audio part (missing part / declared
 * content-type not in the allowed audio set) — BEFORE the file is handed to the media module's
 * {@code UploadOriginalUseCase}. Maps to 422 {@code MEDIA_INVALID}. Studio ADD §5.1 (WU-STU-2).
 *
 * <p>Deeper format checks (magic-byte probing) happen inside the media module and surface their
 * own codes ({@code UNSUPPORTED_FORMAT} / {@code FILE_REJECTED} / {@code PAYLOAD_TOO_LARGE}) via
 * the existing global {@code DomainExceptionMapper} — this module does not re-wrap those.
 */
public class MediaInvalidException extends DomainException {

  public MediaInvalidException(String message) {
    super(ErrorCode.MEDIA_INVALID, message, "audio");
  }
}
