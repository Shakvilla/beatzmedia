package org.shakvilla.beatzmedia.search.domain;

/** Filter scope for {@link QueryService}: mirrors {@link EntityType} members plus ALL (ADD §3). */
public enum SearchScope {
  TRACK,
  ARTIST,
  ALBUM,
  PLAYLIST,
  STORE_ITEM,
  PODCAST,
  EVENT,
  ALL
}
