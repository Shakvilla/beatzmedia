package org.shakvilla.beatzmedia.media.domain;

/**
 * Identifies which delivery rendition is being requested or signed. FULL = the full
 * {@code full.m4a} rendition (owners only); PREVIEW = the server-clipped {@code preview.m4a}
 * (non-owners). INV-3 / ADD §3.
 */
public enum DeliveryVariant {
  FULL,
  PREVIEW
}
