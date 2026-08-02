package org.shakvilla.beatzmedia.media.application.service;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.media.domain.AudioFormat;
import org.shakvilla.beatzmedia.media.domain.ImageFormat;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.UnsupportedFormatException;

/**
 * Magic-byte format detector for uploaded files. Validates that the binary content matches an
 * accepted format. Returns the detected format or throws {@link UnsupportedFormatException}.
 * ADD §9 / LLFR-MEDIA-01.1.
 */
@ApplicationScoped
public class MagicByteValidator {

  // WAV: "RIFF" at offset 0, "WAVE" at offset 8
  private static final byte[] WAV_RIFF = {0x52, 0x49, 0x46, 0x46}; // "RIFF"
  private static final byte[] WAV_WAVE = {0x57, 0x41, 0x56, 0x45}; // "WAVE"

  // FLAC: "fLaC" at offset 0
  private static final byte[] FLAC_MAGIC = {0x66, 0x4C, 0x61, 0x43}; // "fLaC"

  // MP3 has two shapes in the wild and we must admit both (ADR-35):
  //   1. An ID3v2 tag prepended to the audio — "ID3" at offset 0. This is the common case for
  //      anything tagged by a DAW, iTunes, or a distributor.
  //   2. A bare MPEG audio frame, when the file carries no ID3v2 tag (ID3v1 lives in the LAST
  //      128 bytes, so a header probe cannot see it — hence the frame-sync check below).
  private static final byte[] ID3_MAGIC = {0x49, 0x44, 0x33}; // "ID3"

  // PNG: 8-byte magic
  private static final byte[] PNG_MAGIC = {
    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
  };

  // JPEG: SOI marker 0xFFD8, then FF
  private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

  private static final int PROBE_BYTES = 12;

  /**
   * Detect the audio format from the first bytes of the file.
   *
   * @param header at least 12 bytes from the start of the file
   * @return the detected {@link AudioFormat}
   * @throws UnsupportedFormatException if the magic bytes match none of WAV, FLAC or MP3
   */
  public AudioFormat detectAudioFormat(byte[] header) {
    if (header == null || header.length < PROBE_BYTES) {
      throw new UnsupportedFormatException(
          "File too short to detect format; expected WAV, FLAC or MP3 audio");
    }
    if (matchesAt(header, 0, WAV_RIFF) && matchesAt(header, 8, WAV_WAVE)) {
      return AudioFormat.WAV;
    }
    if (matchesAt(header, 0, FLAC_MAGIC)) {
      return AudioFormat.FLAC;
    }
    // Checked last: the frame-sync pattern is only 11 bits, so it is the loosest signature here
    // and must never get the chance to shadow a container with a real magic number.
    if (matchesAt(header, 0, ID3_MAGIC) || isMpegFrameSync(header)) {
      return AudioFormat.MP3;
    }
    throw new UnsupportedFormatException(
        "Unsupported audio format — only WAV, FLAC and MP3 are accepted");
  }

  /**
   * True when the first two bytes are a valid MPEG audio frame header.
   *
   * <p>The sync word is 11 set bits: all of byte 0, then the top three of byte 1. That alone
   * matches roughly one in 2048 random byte pairs, so the version and layer fields are validated
   * too — both have a bit pattern the spec marks *reserved*, and a file using either is not real
   * MPEG audio. Rejecting those cuts most of the false positives an unqualified sync check would
   * let through.
   */
  private static boolean isMpegFrameSync(byte[] header) {
    if ((header[0] & 0xFF) != 0xFF) {
      return false;
    }
    int b1 = header[1] & 0xFF;
    if ((b1 & 0xE0) != 0xE0) {
      return false; // top three bits of byte 1 complete the 11-bit sync word
    }
    int version = (b1 >> 3) & 0x03;
    int layer = (b1 >> 1) & 0x03;
    return version != 0x01 && layer != 0x00; // 01 / 00 are the reserved encodings
  }

  /**
   * Detect the image format from the first bytes of the file.
   *
   * @param header at least 8 bytes from the start of the file
   * @return the detected {@link ImageFormat}
   * @throws UnsupportedFormatException if the magic bytes do not match PNG or JPEG
   */
  public ImageFormat detectImageFormat(byte[] header) {
    if (header == null || header.length < 8) {
      throw new UnsupportedFormatException(
          "File too short to detect format; expected PNG or JPG artwork");
    }
    if (matchesAt(header, 0, PNG_MAGIC)) {
      return ImageFormat.PNG;
    }
    if (matchesAt(header, 0, JPEG_MAGIC)) {
      return ImageFormat.JPG;
    }
    throw new UnsupportedFormatException(
        "Unsupported image format — only PNG and JPG are accepted");
  }

  /**
   * Validate according to kind and return the kind-specific format code as a string,
   * or throw {@link UnsupportedFormatException}. Convenience overload for the use-case layer.
   */
  public void validate(MediaKind kind, byte[] header) {
    if (kind == MediaKind.AUDIO) {
      detectAudioFormat(header);
    } else {
      detectImageFormat(header);
    }
  }

  private boolean matchesAt(byte[] data, int offset, byte[] pattern) {
    if (data.length < offset + pattern.length) {
      return false;
    }
    for (int i = 0; i < pattern.length; i++) {
      if (data[offset + i] != pattern[i]) {
        return false;
      }
    }
    return true;
  }
}
