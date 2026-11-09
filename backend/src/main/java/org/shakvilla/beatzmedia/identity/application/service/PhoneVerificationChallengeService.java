package org.shakvilla.beatzmedia.identity.application.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.application.port.in.FanSettingsView;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.application.port.out.PhoneVerificationChallengeRepository;
import org.shakvilla.beatzmedia.identity.application.port.out.PhoneVerificationSmsSender;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.FanSettings;
import org.shakvilla.beatzmedia.identity.domain.PhoneVerificationChallenge;
import org.shakvilla.beatzmedia.identity.domain.PhoneVerificationStatus;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.platform.domain.RateLimitedException;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/** Transactional persistence operations for phone verification challenges. */
@ApplicationScoped
public class PhoneVerificationChallengeService {
  private static final Duration CODE_TTL = Duration.ofMinutes(5);
  private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
  private static final Duration RATE_WINDOW = Duration.ofHours(1);
  private static final Duration RETENTION = Duration.ofDays(30);
  private static final int MAX_STARTS_PER_ACCOUNT = 3;
  private static final int MAX_STARTS_PER_PHONE = 5;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final AccountRepository accounts;
  private final PhoneVerificationChallengeRepository challenges;
  private final PhoneVerificationSmsSender smsSender;
  private final PhoneVerificationCodeHasher hasher;
  private final Clock clock;
  private final IdGenerator ids;

  @Inject
  public PhoneVerificationChallengeService(
      AccountRepository accounts,
      PhoneVerificationChallengeRepository challenges,
      PhoneVerificationSmsSender smsSender,
      PhoneVerificationCodeHasher hasher,
      Clock clock,
      IdGenerator ids) {
    this.accounts = accounts;
    this.challenges = challenges;
    this.smsSender = smsSender;
    this.hasher = hasher;
    this.clock = clock;
    this.ids = ids;
  }

  @Transactional
  public PreparedChallenge prepare(AccountId accountId, String inputPhone) {
    if (accounts.findById(accountId).isEmpty()) throw new NotFoundException("Account not found");
    String phone = PhoneNumberNormalizer.normalizeGhana(inputPhone);
    Instant now = clock.now();
    FanSettings settings = accounts.findSettings(accountId).orElseGet(() -> FanSettings.defaults(accountId));
    if (phone.equals(settings.getPhone()) && settings.isPhoneVerified()) {
      return new PreparedChallenge(null, phone, null, null, null, true);
    }

    var latest = challenges.findLatestForAccount(accountId);
    if (latest.isPresent()) {
      Instant availableAt = latest.get().createdAt().plus(RESEND_COOLDOWN);
      if (availableAt.isAfter(now)) {
        throw new RateLimitedException("Please wait before requesting another verification code", Duration.between(now, availableAt).toSeconds());
      }
    }
    Instant rateWindowStart = now.minus(RATE_WINDOW);
    if (challenges.countCreatedForAccountSince(accountId, rateWindowStart) >= MAX_STARTS_PER_ACCOUNT
        || challenges.countCreatedForPhoneSince(phone, rateWindowStart) >= MAX_STARTS_PER_PHONE) {
      throw new RateLimitedException("Verification code request limit reached", RATE_WINDOW.toSeconds());
    }

    String id = ids.newId();
    String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    String provider = smsSender.selectedProvider();
    PhoneVerificationChallenge challenge = new PhoneVerificationChallenge(
        id, accountId, phone, hasher.hash(id, code), provider, PhoneVerificationStatus.pending,
        0, now, now.plus(CODE_TTL), null, null);

    challenges.invalidateActive(accountId);
    challenges.save(challenge);
    accounts.saveSettings(copyWithPhone(settings, phone, false));
    challenges.deleteCreatedBefore(now.minus(RETENTION));
    return new PreparedChallenge(challenge, phone, code, provider, now.plus(RESEND_COOLDOWN), false);
  }

  @Transactional
  public void markSent(String challengeId) {
    challenges.findById(challengeId)
        .filter(c -> c.status() == PhoneVerificationStatus.pending)
        .ifPresent(c -> challenges.save(c.sent(clock.now())));
  }

  @Transactional
  public void markFailed(String challengeId) {
    challenges.findById(challengeId)
        .filter(c -> c.status() == PhoneVerificationStatus.pending)
        .ifPresent(c -> challenges.save(c.failed()));
  }

  @Transactional(dontRollbackOn = ValidationException.class)
  public FanSettingsView confirm(AccountId accountId, String inputCode) {
    Instant now = clock.now();
    PhoneVerificationChallenge challenge = challenges.findActive(accountId, now, true)
        .orElseThrow(() -> new ValidationException("The verification code is invalid or expired"));
    FanSettings settings = accounts.findSettings(accountId)
        .orElseGet(() -> FanSettings.defaults(accountId));
    if (!challenge.phone().equals(settings.getPhone())) {
      challenges.save(challenge.expired());
      throw new ValidationException("The verification code is invalid or expired");
    }
    if (challenge.expiresAt().isBefore(now)) {
      challenges.save(challenge.expired());
      throw new ValidationException("The verification code is invalid or expired");
    }
    if (inputCode == null || !hasher.matches(challenge.id(), inputCode, challenge.codeHash())) {
      challenges.save(challenge.incorrectCode());
      throw new ValidationException("The verification code is invalid or expired");
    }

    challenges.save(challenge.consumed(now));
    FanSettings verified = copyWithPhone(settings, challenge.phone(), true);
    accounts.saveSettings(verified);
    return toView(verified);
  }

  private static FanSettings copyWithPhone(FanSettings s, String phone, boolean verified) {
    return new FanSettings(
        s.getAccountId(), s.getTheme(), s.getAudioQuality(), s.getStreamingQuality(),
        s.getDownloadQuality(), s.getCrossfade(), s.isDataSaver(), s.isNewReleases(),
        s.isPlaylistUpdates(), s.isDropsOffers(), s.getCountry(), phone, verified);
  }

  private static FanSettingsView toView(FanSettings s) {
    return new FanSettingsView(
        s.getTheme(), s.getAudioQuality(), s.getStreamingQuality(), s.getDownloadQuality(),
        s.getCrossfade(), s.isDataSaver(), new FanSettingsView.NotificationPrefs(
            s.isNewReleases(), s.isPlaylistUpdates(), s.isDropsOffers()), s.getCountry(),
        s.getPhone(), s.isPhoneVerified());
  }

  public record PreparedChallenge(
      PhoneVerificationChallenge challenge,
      String phone,
      String code,
      String provider,
      Instant resendAvailableAt,
      boolean alreadyVerified) {}
}
