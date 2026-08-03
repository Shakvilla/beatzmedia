package org.shakvilla.beatzmedia.media.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.application.service.MagicByteValidator;


/**
 * LLFR-MEDIA-01.1 — format validation table.
 * AC: WAV, FLAC and MP3 accepted (MP3 per ADR-35); EXE/short/null and malformed frame syncs
 * rejected with UNSUPPORTED_FORMAT.
 */
class MagicByteValidatorTest {

  private final MagicByteValidator validator = new MagicByteValidator();

  // ---- WAV ----

  @Test
  void wav_magic_bytes_accepted() {
    byte[] header = wavHeader();
    AudioFormat fmt = validator.detectAudioFormat(header);
    assertEquals(AudioFormat.WAV, fmt);
  }

  // ---- FLAC ----

  @Test
  void flac_magic_bytes_accepted() {
    byte[] header = flacHeader();
    AudioFormat fmt = validator.detectAudioFormat(header);
    assertEquals(AudioFormat.FLAC, fmt);
  }

  // ---- PNG ----

  @Test
  void png_magic_bytes_accepted() {
    byte[] header = pngHeader();
    ImageFormat fmt = validator.detectImageFormat(header);
    assertEquals(ImageFormat.PNG, fmt);
  }

  // ---- JPG ----

  @Test
  void jpg_magic_bytes_accepted() {
    byte[] header = jpgHeader();
    ImageFormat fmt = validator.detectImageFormat(header);
    assertEquals(ImageFormat.JPG, fmt);
  }

  // ---- MP3 (ADR-35) ----

  @Test
  void mp3_bare_frame_sync_accepted() {
    assertEquals(AudioFormat.MP3, validator.detectAudioFormat(mp3FrameSyncHeader()));
  }

  @Test
  void mp3_with_id3v2_tag_accepted() {
    assertEquals(AudioFormat.MP3, validator.detectAudioFormat(mp3Id3Header()));
  }

  @Test
  void mp3_is_the_only_lossy_accepted_format() {
    // The wizard's quality warning keys off this flag, so a format silently flipping to
    // lossy() == false would drop the warning without any test noticing.
    assertTrue(AudioFormat.MP3.lossy());
    assertFalse(AudioFormat.WAV.lossy());
    assertFalse(AudioFormat.FLAC.lossy());
  }

  @Test
  void frame_sync_with_reserved_version_rejected() {
    // Sync word present, but version bits == 01, which the MPEG spec marks reserved. Accepting
    // this would let an arbitrary 0xFF-leading binary pass as audio.
    byte[] reservedVersion = new byte[]{(byte) 0xFF, (byte) 0xEA, 0x00, 0x00, 0x00, 0x00,
                                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    assertThrows(
        UnsupportedFormatException.class, () -> validator.detectAudioFormat(reservedVersion));
  }

  @Test
  void frame_sync_with_reserved_layer_rejected() {
    // Sync word present, version valid (MPEG-1), but layer bits == 00, also reserved.
    byte[] reservedLayer = new byte[]{(byte) 0xFF, (byte) 0xF9, 0x00, 0x00, 0x00, 0x00,
                                      0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    assertThrows(UnsupportedFormatException.class, () -> validator.detectAudioFormat(reservedLayer));
  }

  // ---- Rejects ----

  @Test
  void exe_rejected_for_audio() {
    byte[] exe = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00,
                             0x00, 0x00, 0x00, 0x00, 0x00, 0x00}; // MZ magic
    assertThrows(UnsupportedFormatException.class, () -> validator.detectAudioFormat(exe));
  }

  @Test
  void too_short_audio_rejected() {
    byte[] tiny = new byte[]{0x01, 0x02};
    assertThrows(UnsupportedFormatException.class, () -> validator.detectAudioFormat(tiny));
  }

  @Test
  void null_audio_rejected() {
    assertThrows(UnsupportedFormatException.class, () -> validator.detectAudioFormat(null));
  }

  @Test
  void mp3_rejected_for_image() {
    // Audio is admitted as audio (ADR-35) but must still never satisfy an ARTWORK upload.
    assertThrows(
        UnsupportedFormatException.class,
        () -> validator.detectImageFormat(mp3FrameSyncHeader()));
  }

  @Test
  void validate_audio_mp3_ok() {
    assertDoesNotThrow(() -> validator.validate(MediaKind.AUDIO, mp3FrameSyncHeader()));
  }

  @Test
  void validate_audio_wav_ok() {
    assertDoesNotThrow(() -> validator.validate(MediaKind.AUDIO, wavHeader()));
  }

  @Test
  void validate_audio_flac_ok() {
    assertDoesNotThrow(() -> validator.validate(MediaKind.AUDIO, flacHeader()));
  }

  @Test
  void validate_artwork_png_ok() {
    assertDoesNotThrow(() -> validator.validate(MediaKind.ARTWORK, pngHeader()));
  }

  @Test
  void validate_artwork_jpg_ok() {
    assertDoesNotThrow(() -> validator.validate(MediaKind.ARTWORK, jpgHeader()));
  }

  // ---- Helpers ----

  static byte[] wavHeader() {
    // "RIFF" at 0, 4 bytes size, "WAVE" at 8
    return new byte[]{
        0x52, 0x49, 0x46, 0x46, // RIFF
        0x00, 0x00, 0x00, 0x00, // size (ignored)
        0x57, 0x41, 0x56, 0x45  // WAVE
    };
  }

  /** A bare MPEG-1 Layer III frame: 11-bit sync, version 11 (MPEG-1), layer 01 (Layer III). */
  static byte[] mp3FrameSyncHeader() {
    return new byte[]{
        (byte) 0xFF, (byte) 0xFB, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };
  }

  /** An MP3 carrying an ID3v2 tag — "ID3" then version/flags/size. The common tagged case. */
  static byte[] mp3Id3Header() {
    return new byte[]{
        0x49, 0x44, 0x33, // "ID3"
        0x03, 0x00, 0x00, // version 2.3, flags
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };
  }

  static byte[] flacHeader() {
    return new byte[]{
        0x66, 0x4C, 0x61, 0x43, // fLaC
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };
  }

  static byte[] pngHeader() {
    return new byte[]{
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG magic
        0x00, 0x00, 0x00, 0x00
    };
  }

  static byte[] jpgHeader() {
    return new byte[]{
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF,
        (byte) 0xE0, // APP0 marker
        0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };
  }
}
