package org.shakvilla.beatzmedia.catalog.application.port.in;

/** A home-trending entry: either a standalone track or one collective album card. */
public record TrendingItemView(String type, TrackView track, AlbumView album, long plays) {
  public static TrendingItemView track(TrackView track) {
    return new TrendingItemView("track", track, null, track.plays() == null ? 0 : track.plays());
  }

  public static TrendingItemView album(String releaseType, AlbumView album, long plays) {
    return new TrendingItemView(releaseType, null, album, plays);
  }
}
