package org.shakvilla.beatzmedia.catalog.domain;

import java.util.List;

/**
 * Playlist aggregate root. Editorial/public; private playlists are hidden as 404 to non-owners.
 * Catalog ADD §3 / LLFR-CATALOG-01.7. Domain-layer; no framework imports.
 */
public final class Playlist {

  private final PlaylistId id;
  private final String title;
  private final String description;
  private final String creator;
  private final String creatorAvatar;
  private final String image;
  private final boolean isPublic;
  private final Long followers;
  /** Ordered track ids in the playlist. */
  private final List<String> trackIds;

  public Playlist(
      PlaylistId id,
      String title,
      String description,
      String creator,
      String creatorAvatar,
      String image,
      boolean isPublic,
      Long followers,
      List<String> trackIds) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.creator = creator;
    this.creatorAvatar = creatorAvatar;
    this.image = image;
    this.isPublic = isPublic;
    this.followers = followers;
    this.trackIds = trackIds;
  }

  public PlaylistId getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getCreator() {
    return creator;
  }

  public String getCreatorAvatar() {
    return creatorAvatar;
  }

  public String getImage() {
    return image;
  }

  public boolean isPublic() {
    return isPublic;
  }

  public Long getFollowers() {
    return followers;
  }

  public List<String> getTrackIds() {
    return trackIds;
  }
}
