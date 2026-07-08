package org.shakvilla.beatzmedia.studio.application.service;

import java.time.Instant;
import java.util.List;

import org.shakvilla.beatzmedia.platform.domain.Genre;
import org.shakvilla.beatzmedia.studio.application.port.in.SaveStudioProfileCommand;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioLinks;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioPressAsset;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioProfileView;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioShow;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.PressAsset;
import org.shakvilla.beatzmedia.studio.domain.ProfileLinks;
import org.shakvilla.beatzmedia.studio.domain.ShowAppearance;
import org.shakvilla.beatzmedia.studio.domain.StudioProfile;

/**
 * Maps between the {@link StudioProfile} domain aggregate and its wire read-model /
 * command shapes. Genre wire-string ↔ {@link Genre} conversion happens HERE (mirrors {@code
 * EventMapper} / {@code EventCategory} in the events module). Studio ADD §6.
 */
final class StudioProfileMapper {

  private StudioProfileMapper() {}

  static StudioProfileView toView(StudioProfile profile) {
    return new StudioProfileView(
        profile.displayName(),
        profile.username(),
        profile.hometown(),
        profile.genres().stream().map(Genre::wireValue).toList(),
        profile.bio(),
        profile.avatarUrl(),
        profile.bannerUrl(),
        toView(profile.links()),
        profile.shows().stream().map(StudioProfileMapper::toView).toList(),
        profile.featuredTrackId(),
        profile.bookingEmail(),
        profile.pressAssets().stream().map(StudioProfileMapper::toView).toList());
  }

  private static StudioLinks toView(ProfileLinks links) {
    return new StudioLinks(links.instagram(), links.twitter(), links.youtube(), links.website());
  }

  private static StudioShow toView(ShowAppearance show) {
    return new StudioShow(show.id(), show.venue(), show.date(), show.city());
  }

  private static StudioPressAsset toView(PressAsset asset) {
    return new StudioPressAsset(asset.id(), asset.name(), asset.url());
  }

  /** Builds the domain aggregate from a validated command (genres already checked by the caller). */
  static StudioProfile toDomain(ArtistId artist, SaveStudioProfileCommand cmd, Instant updatedAt) {
    List<Genre> genres =
        cmd.genres() == null ? List.of() : cmd.genres().stream().map(Genre::fromWireValue).toList();
    ProfileLinks links = cmd.links() == null
        ? ProfileLinks.empty()
        : new ProfileLinks(
            cmd.links().instagram(), cmd.links().twitter(), cmd.links().youtube(),
            cmd.links().website());
    List<ShowAppearance> shows = cmd.shows() == null
        ? List.of()
        : cmd.shows().stream()
            .map(s -> new ShowAppearance(s.id(), s.venue(), s.date(), s.city()))
            .toList();
    List<PressAsset> pressAssets = cmd.pressAssets() == null
        ? List.of()
        : cmd.pressAssets().stream()
            .map(a -> new PressAsset(a.id(), a.name(), a.url()))
            .toList();
    return new StudioProfile(
        artist,
        cmd.username(),
        cmd.displayName(),
        cmd.hometown(),
        genres,
        cmd.bio(),
        cmd.avatar(),
        cmd.banner(),
        links,
        shows,
        cmd.featuredTrackId(),
        cmd.bookingEmail(),
        pressAssets,
        updatedAt);
  }
}
