package org.shakvilla.beatzmedia.studio.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.studio.application.port.in.GetStudioSettings;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioSettingsView;
import org.shakvilla.beatzmedia.studio.application.port.out.AccountReader;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.StudioSettings;

/**
 * Application service for LLFR-STUDIO-04.2 (studio settings read). {@code artist} is always the
 * caller resolved from the JWT subject — there is no foreign-settings read path. Never 404s: a
 * not-yet-configured settings object resolves to a blank shell. Composes the honest static/derived
 * Category B fields via {@link StudioSettingsMapper} (studio.md §16). Studio ADD §4.1 / §9 / §16.
 */
@ApplicationScoped
public class GetStudioSettingsService implements GetStudioSettings {

  private final StudioRepository repository;
  private final AccountReader accountReader;

  @Inject
  public GetStudioSettingsService(StudioRepository repository, AccountReader accountReader) {
    this.repository = repository;
    this.accountReader = accountReader;
  }

  @Override
  @Transactional
  public StudioSettingsView get(ArtistId artist) {
    StudioSettings settings = repository.findSettings(artist).orElseGet(() -> StudioSettings.blank(artist));
    String email = accountReader.emailOf(artist);
    return StudioSettingsMapper.toView(settings, email);
  }
}
