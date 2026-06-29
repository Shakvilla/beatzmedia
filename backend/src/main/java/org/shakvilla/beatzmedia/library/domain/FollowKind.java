package org.shakvilla.beatzmedia.library.domain;

/**
 * Discriminates what entity kind is being followed. One table per kind in the schema. Library ADD §3.
 */
public enum FollowKind {
  artist,
  playlist,
  show
}
