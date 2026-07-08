package org.shakvilla.beatzmedia.studio.adapter.out.persistence;

import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.platform.domain.Genre;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.PressAsset;
import org.shakvilla.beatzmedia.studio.domain.ProfileLinks;
import org.shakvilla.beatzmedia.studio.domain.ShowAppearance;
import org.shakvilla.beatzmedia.studio.domain.StudioProfile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps {@code studio_profile} JPA entity ↔ {@link StudioProfile} domain aggregate. Domain carries no
 * ORM/Jackson annotations (ArchUnit-enforced); this is the only place the mapping happens. {@code
 * links}/{@code shows}/{@code press_assets} persist as {@code jsonb} (Studio ADD §5.2 / §7).
 */
@ApplicationScoped
public class StudioProfileEntityMapper {

  private final ObjectMapper objectMapper;

  @Inject
  public StudioProfileEntityMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  StudioProfile toDomain(StudioProfileEntity e) {
    List<Genre> genres = e.genres == null
        ? List.of()
        : Arrays.stream(e.genres).map(Genre::fromWireValue).toList();
    return new StudioProfile(
        new ArtistId(e.artistId),
        e.username,
        e.displayName,
        e.hometown,
        genres,
        e.bio,
        e.avatarUrl,
        e.bannerUrl,
        readLinks(e.linksJson),
        readShows(e.showsJson),
        e.featuredTrackId,
        e.bookingEmail,
        readPressAssets(e.pressAssetsJson),
        e.updatedAt);
  }

  StudioProfileEntity toEntity(StudioProfile profile, StudioProfileEntity target) {
    StudioProfileEntity entity = target != null ? target : new StudioProfileEntity();
    entity.artistId = profile.artistId().value();
    entity.username = profile.username();
    entity.displayName = profile.displayName();
    entity.hometown = profile.hometown();
    entity.genres = profile.genres().stream().map(Genre::wireValue).toArray(String[]::new);
    entity.bio = profile.bio();
    entity.avatarUrl = profile.avatarUrl();
    entity.bannerUrl = profile.bannerUrl();
    entity.linksJson = writeLinks(profile.links());
    entity.showsJson = writeShows(profile.shows());
    entity.featuredTrackId = profile.featuredTrackId();
    entity.bookingEmail = profile.bookingEmail();
    entity.pressAssetsJson = writePressAssets(profile.pressAssets());
    entity.updatedAt = profile.updatedAt();
    return entity;
  }

  private ProfileLinks readLinks(String json) {
    if (json == null || json.isBlank()) {
      return ProfileLinks.empty();
    }
    try {
      return objectMapper.readValue(json, ProfileLinks.class);
    } catch (Exception e) {
      return ProfileLinks.empty();
    }
  }

  private String writeLinks(ProfileLinks links) {
    try {
      return objectMapper.writeValueAsString(links == null ? ProfileLinks.empty() : links);
    } catch (Exception e) {
      return "{}";
    }
  }

  private List<ShowAppearance> readShows(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<List<ShowAppearance>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }

  private String writeShows(List<ShowAppearance> shows) {
    try {
      return objectMapper.writeValueAsString(shows == null ? List.of() : shows);
    } catch (Exception e) {
      return "[]";
    }
  }

  private List<PressAsset> readPressAssets(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<List<PressAsset>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }

  private String writePressAssets(List<PressAsset> assets) {
    try {
      return objectMapper.writeValueAsString(assets == null ? List.of() : assets);
    } catch (Exception e) {
      return "[]";
    }
  }
}
