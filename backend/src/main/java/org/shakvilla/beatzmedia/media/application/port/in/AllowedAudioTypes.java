package org.shakvilla.beatzmedia.media.application.port.in;

import java.util.Locale;
import java.util.Set;

/**
 * Declared (client-reported) content types accepted for audio uploads, checked by callers
 * (catalog's track upload, studio's episode upload) BEFORE handing the part to {@link
 * UploadOriginalUseCase}. This is a coarse, spoofable pre-check — {@code
 * org.shakvilla.beatzmedia.media.application.service.MagicByteValidator} is the real enforcement,
 * sniffing the actual bytes downstream. LLFR-MEDIA-01.1.
 *
 * <p><b>This set must stay in lockstep with {@code MagicByteValidator.detectAudioFormat}.</b> Both
 * callers previously inlined their own copy of the allowlist, so widening the validator to admit
 * MP3 (ADR-35) left every real upload still failing 422 at these gates — the validator was never
 * reached. Listing a type here that the validator rejects is just as broken in the other
 * direction: the upload would be admitted, then fail deeper with a less obvious error. Hence
 * exactly WAV, FLAC and MP3, and one shared definition rather than three.
 */
public final class AllowedAudioTypes {

  public static final Set<String> CONTENT_TYPES =
      Set.of(
          "audio/wav",
          "audio/x-wav",
          "audio/wave",
          "audio/vnd.wave",
          "audio/flac",
          "audio/x-flac",
          "audio/mpeg",
          "audio/mp3",
          "audio/x-mp3",
          "audio/mpeg3",
          "audio/x-mpeg-3");

  /** Human-readable list for error messages, so the API never names a format it won't take. */
  public static final String DISPLAY = "WAV/FLAC/MP3";

  private AllowedAudioTypes() {}

  public static boolean isAllowed(String contentType) {
    return contentType != null && CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
  }
}
