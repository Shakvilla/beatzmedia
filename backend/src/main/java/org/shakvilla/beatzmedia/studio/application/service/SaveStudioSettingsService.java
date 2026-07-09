package org.shakvilla.beatzmedia.studio.application.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.studio.application.port.in.SaveStudioSettings;
import org.shakvilla.beatzmedia.studio.application.port.in.SaveStudioSettingsCommand;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioSettingsView;
import org.shakvilla.beatzmedia.studio.application.port.out.AccountReader;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.StudioSettings;

/**
 * Application service for LLFR-STUDIO-04.2 (studio settings save). {@code PUT /studio/settings} is
 * a natural upsert (no {@code Idempotency-Key}): the same request replayed twice yields the same
 * saved state — mirrors {@code SaveStudioProfileService}. Only Category A fields ({@code
 * notifications}, {@code defaults}, {@code payouts}, {@code privacy}, {@code team}) are persisted;
 * {@code team[].role} membership and non-negative money are enforced by Bean Validation at the REST
 * boundary (422 {@code VALIDATION}) before this service is ever invoked. Appends exactly one {@code
 * AuditEntry} (INV-10) atomically in the same transaction, mirroring {@code UpdateEpisodeService}.
 * Studio ADD §4.1 / §9 / §16.
 */
@ApplicationScoped
public class SaveStudioSettingsService implements SaveStudioSettings {

  private final StudioRepository repository;
  private final AccountReader accountReader;
  private final Clock clock;
  private final IdGenerator ids;
  private final AuditWriter auditWriter;

  @Inject
  public SaveStudioSettingsService(
      StudioRepository repository,
      AccountReader accountReader,
      Clock clock,
      IdGenerator ids,
      AuditWriter auditWriter) {
    this.repository = repository;
    this.accountReader = accountReader;
    this.clock = clock;
    this.ids = ids;
    this.auditWriter = auditWriter;
  }

  @Override
  @Transactional
  public StudioSettingsView save(ArtistId artist, SaveStudioSettingsCommand cmd) {
    Instant now = clock.now();

    StudioSettings settings = StudioSettingsMapper.toDomain(artist, cmd, now);
    StudioSettings saved = repository.saveSettings(settings);

    // INV-10: audit privileged mutation atomically in the same transaction.
    auditWriter.append(new AuditEntry(
        ids.newId(), artist.value(), "UPDATE_STUDIO_SETTINGS", "StudioSettings", artist.value(),
        AuditType.SETTINGS, null, now));

    String email = accountReader.emailOf(artist);
    return StudioSettingsMapper.toView(saved, email);
  }
}
