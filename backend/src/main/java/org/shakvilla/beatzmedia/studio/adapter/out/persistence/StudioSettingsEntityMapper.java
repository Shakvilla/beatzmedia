package org.shakvilla.beatzmedia.studio.adapter.out.persistence;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.NotificationPrefs;
import org.shakvilla.beatzmedia.studio.domain.PayoutSettings;
import org.shakvilla.beatzmedia.studio.domain.PrivacySettings;
import org.shakvilla.beatzmedia.studio.domain.StudioDefaults;
import org.shakvilla.beatzmedia.studio.domain.StudioSettings;
import org.shakvilla.beatzmedia.studio.domain.TeamMember;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps {@code studio_settings} JPA entity ↔ {@link StudioSettings} domain aggregate. Domain carries
 * no ORM/Jackson annotations (ArchUnit-enforced); this is the only place the mapping happens. {@code
 * notifications}/{@code defaults}/{@code payouts}/{@code privacy}/{@code team} persist as {@code
 * jsonb} — same idiom as {@code StudioProfileEntityMapper}'s {@code links}/{@code shows}/{@code
 * press_assets}. Studio ADD §5.2 / §7.
 */
@ApplicationScoped
public class StudioSettingsEntityMapper {

  private final ObjectMapper objectMapper;

  @Inject
  public StudioSettingsEntityMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  StudioSettings toDomain(StudioSettingsEntity e) {
    return new StudioSettings(
        new ArtistId(e.artistId),
        readNotifications(e.notificationsJson),
        readDefaults(e.defaultsJson),
        readPayouts(e.payoutsJson),
        readPrivacy(e.privacyJson),
        readTeam(e.teamJson),
        e.updatedAt);
  }

  StudioSettingsEntity toEntity(StudioSettings settings, StudioSettingsEntity target) {
    StudioSettingsEntity entity = target != null ? target : new StudioSettingsEntity();
    entity.artistId = settings.artistId().value();
    entity.notificationsJson = writeNotifications(settings.notifications());
    entity.defaultsJson = writeDefaults(settings.defaults());
    entity.payoutsJson = writePayouts(settings.payouts());
    entity.privacyJson = writePrivacy(settings.privacy());
    entity.teamJson = writeTeam(settings.team());
    entity.updatedAt = settings.updatedAt();
    return entity;
  }

  private NotificationPrefs readNotifications(String json) {
    if (json == null || json.isBlank()) {
      return NotificationPrefs.blank();
    }
    try {
      return objectMapper.readValue(json, NotificationPrefs.class);
    } catch (Exception e) {
      return NotificationPrefs.blank();
    }
  }

  private String writeNotifications(NotificationPrefs prefs) {
    try {
      return objectMapper.writeValueAsString(prefs == null ? NotificationPrefs.blank() : prefs);
    } catch (Exception e) {
      return "{}";
    }
  }

  private StudioDefaults readDefaults(String json) {
    if (json == null || json.isBlank()) {
      return StudioDefaults.blank();
    }
    try {
      return objectMapper.readValue(json, StudioDefaults.class);
    } catch (Exception e) {
      return StudioDefaults.blank();
    }
  }

  private String writeDefaults(StudioDefaults defaults) {
    try {
      return objectMapper.writeValueAsString(defaults == null ? StudioDefaults.blank() : defaults);
    } catch (Exception e) {
      return "{}";
    }
  }

  private PayoutSettings readPayouts(String json) {
    if (json == null || json.isBlank()) {
      return PayoutSettings.blank();
    }
    try {
      return objectMapper.readValue(json, PayoutSettings.class);
    } catch (Exception e) {
      return PayoutSettings.blank();
    }
  }

  private String writePayouts(PayoutSettings payouts) {
    try {
      return objectMapper.writeValueAsString(payouts == null ? PayoutSettings.blank() : payouts);
    } catch (Exception e) {
      return "{}";
    }
  }

  private PrivacySettings readPrivacy(String json) {
    if (json == null || json.isBlank()) {
      return PrivacySettings.blank();
    }
    try {
      return objectMapper.readValue(json, PrivacySettings.class);
    } catch (Exception e) {
      return PrivacySettings.blank();
    }
  }

  private String writePrivacy(PrivacySettings privacy) {
    try {
      return objectMapper.writeValueAsString(privacy == null ? PrivacySettings.blank() : privacy);
    } catch (Exception e) {
      return "{}";
    }
  }

  private List<TeamMember> readTeam(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<List<TeamMember>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }

  private String writeTeam(List<TeamMember> team) {
    try {
      return objectMapper.writeValueAsString(team == null ? List.of() : team);
    } catch (Exception e) {
      return "[]";
    }
  }
}
