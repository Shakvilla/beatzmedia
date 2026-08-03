package org.shakvilla.beatzmedia.playback.domain;

/**
 * Which audio rendition the server will sign for the caller. Drives {@code MediaService} — FULL
 * signs {@code full.m4a}, PREVIEW signs the server-clipped {@code preview.m4a}. Never selected by
 * the client (INV-3). Playback ADD §3.
 */
public enum PlaybackMode {
  FULL,
  PREVIEW
}
