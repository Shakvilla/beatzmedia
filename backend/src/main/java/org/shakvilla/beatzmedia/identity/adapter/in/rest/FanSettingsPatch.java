package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * REST request DTO for PATCH /v1/me/settings. All fields are optional (null = not provided = keep
 * existing). Bean Validation constraints enforce the contract shape; violations produce 422 with
 * {@code error.field}. Identity ADD §5.1 / §6.
 */
public record FanSettingsPatch(
    @Pattern(regexp = "^(light|dark|system)$", message = "must be one of: light, dark, system")
    String theme,

    @Size(max = 100, message = "must be at most 100 characters")
    String audioQuality,

    @Size(max = 100, message = "must be at most 100 characters")
    String streamingQuality,

    @Size(max = 100, message = "must be at most 100 characters")
    String downloadQuality,

    @Size(max = 100, message = "must be at most 100 characters")
    String crossfade,

    Boolean dataSaver,

    NotificationsDto notifications,

    @Size(min = 1, max = 100, message = "must be between 1 and 100 characters")
    String country,

    @Pattern(
        regexp = "^\\+?[0-9]{7,15}$",
        message = "must be a valid phone number (7-15 digits, optional leading +)")
    String phone) {

  /** Nullable sub-object for partial notification prefs patch. */
  public record NotificationsDto(
      Boolean newReleases,
      Boolean playlistUpdates,
      Boolean dropsOffers) {}
}
