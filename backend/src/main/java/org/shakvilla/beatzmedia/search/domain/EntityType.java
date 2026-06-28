package org.shakvilla.beatzmedia.search.domain;

/** Partitions the single search_document index table by entity kind (ADD §3). */
public enum EntityType {
  TRACK,
  ARTIST,
  ALBUM,
  PLAYLIST,
  STORE_ITEM,
  PODCAST,
  EVENT
}
