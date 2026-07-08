package org.shakvilla.beatzmedia.studio.domain;

import java.time.Instant;
import java.util.List;

import org.shakvilla.beatzmedia.platform.domain.Genre;

/**
 * Studio profile aggregate root — one per artist (Studio ADD §3 / §4.1, LLFR-STUDIO-01.1). Domain
 * layer; no framework imports. {@code username} uniqueness and slug format, and {@code genres}
 * taxonomy membership, are business rules enforced by the application layer ({@code
 * SaveStudioProfileService}) and Bean Validation at the REST boundary — this constructor only
 * guards structural invariants (non-null id, non-null collections), matching the permissive-domain
 * / validating-service pattern used elsewhere in the codebase (e.g. {@code catalog.SubmitRelease}).
 */
public final class StudioProfile {

  private final ArtistId artistId;
  private final String username;
  private final String displayName;
  private final String hometown;
  private final List<Genre> genres;
  private final String bio;
  private final String avatarUrl;
  private final String bannerUrl;
  private final ProfileLinks links;
  private final List<ShowAppearance> shows;
  private final String featuredTrackId;
  private final String bookingEmail;
  private final List<PressAsset> pressAssets;
  private final Instant updatedAt;

  public StudioProfile(
      ArtistId artistId,
      String username,
      String displayName,
      String hometown,
      List<Genre> genres,
      String bio,
      String avatarUrl,
      String bannerUrl,
      ProfileLinks links,
      List<ShowAppearance> shows,
      String featuredTrackId,
      String bookingEmail,
      List<PressAsset> pressAssets,
      Instant updatedAt) {
    if (artistId == null) {
      throw new IllegalArgumentException("artistId must not be null");
    }
    this.artistId = artistId;
    this.username = username == null ? "" : username;
    this.displayName = displayName == null ? "" : displayName;
    this.hometown = hometown;
    this.genres = genres == null ? List.of() : List.copyOf(genres);
    this.bio = bio;
    this.avatarUrl = avatarUrl;
    this.bannerUrl = bannerUrl;
    this.links = links == null ? ProfileLinks.empty() : links;
    this.shows = shows == null ? List.of() : List.copyOf(shows);
    this.featuredTrackId = featuredTrackId;
    this.bookingEmail = bookingEmail;
    this.pressAssets = pressAssets == null ? List.of() : List.copyOf(pressAssets);
    this.updatedAt = updatedAt;
  }

  /**
   * A not-yet-configured profile for an artist who has never called {@code PUT /studio/profile}.
   * {@code GET /studio/profile} never 404s (Studio ADD §5.1 endpoint table has no 404 for this
   * route) — it resolves to this blank shell instead.
   */
  public static StudioProfile blank(ArtistId artistId) {
    return new StudioProfile(
        artistId, "", "", null, List.of(), null, null, null, ProfileLinks.empty(), List.of(), null,
        null, List.of(), null);
  }

  public ArtistId artistId() {
    return artistId;
  }

  public String username() {
    return username;
  }

  public String displayName() {
    return displayName;
  }

  public String hometown() {
    return hometown;
  }

  public List<Genre> genres() {
    return genres;
  }

  public String bio() {
    return bio;
  }

  public String avatarUrl() {
    return avatarUrl;
  }

  public String bannerUrl() {
    return bannerUrl;
  }

  public ProfileLinks links() {
    return links;
  }

  public List<ShowAppearance> shows() {
    return shows;
  }

  public String featuredTrackId() {
    return featuredTrackId;
  }

  public String bookingEmail() {
    return bookingEmail;
  }

  public List<PressAsset> pressAssets() {
    return pressAssets;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
