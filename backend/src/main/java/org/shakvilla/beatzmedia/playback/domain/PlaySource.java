package org.shakvilla.beatzmedia.playback.domain;

/**
 * Where a recorded play originated. Recorded for anti-inflation / bot-play analysis (Playback ADD
 * §9); does not affect the ownership/preview decision. Wire values match the frontend/API contract
 * exactly (lower-case).
 */
public enum PlaySource {
  player,
  preview,
  autoplay
}
