package org.shakvilla.beatzmedia.media.domain;

/**
 * Identifies which delivery rendition is being requested or signed. FULL = full HLS rendition
 * (owners only); PREVIEW = server-clipped ≤30s rendition (non-owners). INV-3 / ADD §3.
 */
public enum DeliveryVariant {
  FULL,
  PREVIEW
}
