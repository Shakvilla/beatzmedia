package org.shakvilla.beatzmedia.playback.domain;

/**
 * The commercial kind of a track as seen by playback (intrinsic; not per-caller). Sourced from
 * {@code CatalogReader}; playback persists no ownership state itself. Playback ADD §3/§4.2.
 */
public enum TrackOwnership {
  FREE,
  FOR_SALE
}
