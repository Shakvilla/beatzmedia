package org.shakvilla.beatzmedia.studio.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.studio.application.port.in.GetStudioProfile;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioProfileView;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.StudioProfile;

/**
 * Application service for LLFR-STUDIO-01.1 (studio profile read). {@code artist} is always the
 * caller resolved from the JWT subject — there is no foreign-profile read path for {@code
 * /studio/profile} (unlike show/episode resources in later WUs), so there is no ownership
 * re-check to perform beyond that resolution. Never 404s: a not-yet-configured profile resolves to
 * a blank shell. Studio ADD §4.1 / §9.
 */
@ApplicationScoped
public class GetStudioProfileService implements GetStudioProfile {

  private final StudioRepository repository;

  @Inject
  public GetStudioProfileService(StudioRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public StudioProfileView get(ArtistId artist) {
    StudioProfile profile = repository.findProfile(artist).orElseGet(() -> StudioProfile.blank(artist));
    return StudioProfileMapper.toView(profile);
  }
}
