package org.shakvilla.beatzmedia.media.domain;

/**
 * Identifies which delivery rendition is being requested or signed. FULL = the full
 * {@code full.m4a} rendition (owners only); PREVIEW = the server-clipped {@code preview.m4a}
 * (non-owners). INV-3 / ADD §3.
 */
public enum DeliveryVariant {
  FULL,
  PREVIEW,
  /**
   * The {@code lossless.flac} rendition — the download payload, owners only, and only when the
   * grant permits it. Separate from FULL because FULL is AAC 128k: serving it as "the download"
   * would hand over a lossy file on a platform selling lossless masters.
   */
  LOSSLESS
}
