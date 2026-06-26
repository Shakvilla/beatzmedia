package org.shakvilla.beatzmedia.catalog.domain;

import java.util.List;

/**
 * Artist profile aggregate root. Catalog ADD §3. Domain-layer; no framework imports.
 */
public final class ArtistProfile {

  private final ArtistId id;
  private final String name;
  private final String image;
  private final String coverImage;
  private final boolean verified;
  private final Long monthlyListeners;
  private final Long followers;
  private final String bio;
  private final String location;
  private final List<String> genres;
  private final List<Show> shows;

  public ArtistProfile(
      ArtistId id,
      String name,
      String image,
      String coverImage,
      boolean verified,
      Long monthlyListeners,
      Long followers,
      String bio,
      String location,
      List<String> genres,
      List<Show> shows) {
    this.id = id;
    this.name = name;
    this.image = image;
    this.coverImage = coverImage;
    this.verified = verified;
    this.monthlyListeners = monthlyListeners;
    this.followers = followers;
    this.bio = bio;
    this.location = location;
    this.genres = genres;
    this.shows = shows;
  }

  public ArtistId getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getImage() {
    return image;
  }

  public String getCoverImage() {
    return coverImage;
  }

  public boolean isVerified() {
    return verified;
  }

  public Long getMonthlyListeners() {
    return monthlyListeners;
  }

  public Long getFollowers() {
    return followers;
  }

  public String getBio() {
    return bio;
  }

  public String getLocation() {
    return location;
  }

  public List<String> getGenres() {
    return genres;
  }

  public List<Show> getShows() {
    return shows;
  }
}
