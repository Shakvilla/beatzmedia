package org.shakvilla.beatzmedia.identity.application.service;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.identity.application.port.in.StartPhoneVerification;
import org.shakvilla.beatzmedia.identity.application.port.in.StartPhoneVerification.StartPhoneVerificationView;
import org.shakvilla.beatzmedia.identity.application.port.out.PhoneVerificationSmsSender;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.PhoneVerificationDeliveryException;
import org.shakvilla.beatzmedia.notifications.domain.PermanentDeliveryException;
import org.shakvilla.beatzmedia.notifications.domain.TransientDeliveryException;

/** Persists a challenge, sends the SMS after that transaction commits, then records acceptance. */
@ApplicationScoped
public class StartPhoneVerificationService implements StartPhoneVerification {
  private final PhoneVerificationChallengeService challengeService;
  private final PhoneVerificationSmsSender smsSender;

  @Inject
  public StartPhoneVerificationService(
      PhoneVerificationChallengeService challengeService,
      PhoneVerificationSmsSender smsSender) {
    this.challengeService = challengeService;
    this.smsSender = smsSender;
  }

  @Override
  public StartPhoneVerificationView start(AccountId accountId, String phone) {
    PhoneVerificationChallengeService.PreparedChallenge prepared = challengeService.prepare(accountId, phone);
    if (prepared.alreadyVerified()) {
      return new StartPhoneVerificationView(mask(prepared.phone()), true, null, null);
    }
    String message = "Your BeatzClik verification code is " + prepared.code()
        + ". It expires in 5 minutes.";
    try {
      smsSender.send(prepared.phone(), message, "phone-verify-" + prepared.challenge().id(), prepared.provider());
      challengeService.markSent(prepared.challenge().id());
    } catch (TransientDeliveryException | PermanentDeliveryException e) {
      challengeService.markFailed(prepared.challenge().id());
      throw new PhoneVerificationDeliveryException();
    } catch (RuntimeException e) {
      challengeService.markFailed(prepared.challenge().id());
      throw e;
    }
    return new StartPhoneVerificationView(
        mask(prepared.phone()), false, prepared.challenge().expiresAt(), prepared.resendAvailableAt());
  }

  private static String mask(String phone) {
    if (phone == null || phone.length() < 7) return "***";
    return phone.substring(0, 4) + "••••" + phone.substring(phone.length() - 3);
  }
}
