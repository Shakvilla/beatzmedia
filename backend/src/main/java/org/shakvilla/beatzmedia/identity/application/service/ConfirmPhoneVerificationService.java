package org.shakvilla.beatzmedia.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.identity.application.port.in.ConfirmPhoneVerification;
import org.shakvilla.beatzmedia.identity.application.port.in.FanSettingsView;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Input-port implementation for one-time phone code confirmation. */
@ApplicationScoped
public class ConfirmPhoneVerificationService implements ConfirmPhoneVerification {
  private final PhoneVerificationChallengeService challengeService;

  @Inject
  public ConfirmPhoneVerificationService(PhoneVerificationChallengeService challengeService) {
    this.challengeService = challengeService;
  }

  @Override
  public FanSettingsView confirm(AccountId accountId, String code) {
    return challengeService.confirm(accountId, code);
  }
}
