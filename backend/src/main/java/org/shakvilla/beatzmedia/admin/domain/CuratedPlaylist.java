package org.shakvilla.beatzmedia.admin.domain;

/**
 * A curated-playlist reference managed by editorial staff. Pure Java, no framework imports. This
 * is an editorial reference only (name + id) — the actual playlist contents live in {@code
 * library}/{@code catalog} and are out of scope for this WU. Admin ADD §3 / LLFR-ADMIN-06.1.
 */
public final class CuratedPlaylist {

  private final String id;
  private final String name;

  public CuratedPlaylist(String id, String name) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("CuratedPlaylist id must not be blank");
    }
    if (name == null || name.isBlank()) {
      throw new BlankPlaylistNameException();
    }
    this.id = id;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
