package org.shakvilla.beatzmedia.playback.domain;

/**
 * Which audio rendition the server will sign for the caller. Drives {@code MediaService} — FULL
 * signs the full HLS rendition, PREVIEW signs the server-clipped ≤30s rendition. Never selected by
 * the client (INV-3). Playback ADD §3.
 */
public enum PlaybackMode {
  FULL,
  PREVIEW
}
