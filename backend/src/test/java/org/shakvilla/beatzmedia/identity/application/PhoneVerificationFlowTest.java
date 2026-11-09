package org.shakvilla.beatzmedia.identity.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.out.PhoneVerificationChallengeRepository;
import org.shakvilla.beatzmedia.identity.application.port.out.PhoneVerificationSmsSender;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.domain.PhoneVerificationChallenge;
import org.shakvilla.beatzmedia.identity.domain.PhoneVerificationStatus;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

@Tag("unit")
class PhoneVerificationFlowTest {
  private static final AccountId ACCOUNT_ID = new AccountId("phone-fan");
  private FakeAccountRepository accounts;
  private InMemoryChallenges challenges;
  private CapturingSms sms;
  private FakeClock clock;
  private PhoneVerificationChallengeService challengeService;
  private StartPhoneVerificationService startService;

  @BeforeEach
  void setUp() {
    accounts = new FakeAccountRepository();
    accounts.seed(Account.createFan(
        ACCOUNT_ID, "Listener", "listener@example.com", new Credential(ACCOUNT_ID, "hash"),
        Instant.parse("2026-01-01T00:00:00Z")));
    challenges = new InMemoryChallenges();
    sms = new CapturingSms();
    clock = FakeClock.at("2026-07-01T12:00:00Z");
    PhoneVerificationCodeHasher hasher = new PhoneVerificationCodeHasher(
        "ZGV2LWtleS1vbmx5LW5vdC1mb3ItcHJvZHVjdGlvbi0zMi1ieXRlcyE=");
    challengeService = new PhoneVerificationChallengeService(
        accounts, challenges, sms, hasher, clock, FakeIds.sequential("challenge"));
    startService = new StartPhoneVerificationService(challengeService, sms);
  }

  @Test
  void startsSendsAndConfirmsTheSamePhoneCodeOnce() {
    var started = startService.start(ACCOUNT_ID, "0244 000 111");
    String code = captureCode(sms.message);

    assertEquals("+233244000111", accounts.findSettings(ACCOUNT_ID).orElseThrow().getPhone());
    assertFalse(accounts.findSettings(ACCOUNT_ID).orElseThrow().isPhoneVerified());
    assertEquals("+233••••111", started.maskedPhone());
    assertEquals(PhoneVerificationStatus.sent, challenges.latest(ACCOUNT_ID).orElseThrow().status());

    var verified = challengeService.confirm(ACCOUNT_ID, code);

    assertTrue(verified.phoneVerified());
    assertEquals("+233244000111", verified.phone());
    assertEquals(PhoneVerificationStatus.consumed, challenges.latest(ACCOUNT_ID).orElseThrow().status());
    assertThrows(ValidationException.class, () -> challengeService.confirm(ACCOUNT_ID, code));
  }

  @Test
  void wrongCodeAttemptsArePersistedAndChallengeLocksAfterFive() {
    startService.start(ACCOUNT_ID, "0244000111");
    String actual = captureCode(sms.message);
    String incorrect = "000000".equals(actual) ? "000001" : "000000";

    for (int attempt = 0; attempt < 5; attempt++) {
      assertThrows(ValidationException.class, () -> challengeService.confirm(ACCOUNT_ID, incorrect));
    }

    PhoneVerificationChallenge failed = challenges.latest(ACCOUNT_ID).orElseThrow();
    assertEquals(5, failed.attempts());
    assertEquals(PhoneVerificationStatus.failed, failed.status());
    assertFalse(accounts.findSettings(ACCOUNT_ID).orElseThrow().isPhoneVerified());
  }

  @Test
  void expiredCodesCannotVerify() {
    startService.start(ACCOUNT_ID, "0244000111");
    clock.advanceSeconds(301);

    assertThrows(ValidationException.class,
        () -> challengeService.confirm(ACCOUNT_ID, captureCode(sms.message)));
    assertFalse(accounts.findSettings(ACCOUNT_ID).orElseThrow().isPhoneVerified());
  }

  private static String captureCode(String message) {
    Matcher matcher = Pattern.compile("code is ([0-9]{6})").matcher(message);
    if (!matcher.find()) throw new AssertionError("verification SMS did not contain its six-digit code");
    return matcher.group(1);
  }

  private static final class CapturingSms implements PhoneVerificationSmsSender {
    private String message;
    @Override public String selectedProvider() { return "capture"; }
    @Override public void send(String phone, String body, String messageId, String provider) {
      this.message = body;
      assertEquals("+233244000111", phone);
      assertEquals("capture", provider);
      assertTrue(messageId.startsWith("phone-verify-"));
    }
  }

  private static final class InMemoryChallenges implements PhoneVerificationChallengeRepository {
    private final List<PhoneVerificationChallenge> items = new ArrayList<>();

    Optional<PhoneVerificationChallenge> latest(AccountId accountId) {
      return findLatestForAccount(accountId);
    }

    @Override
    public Optional<PhoneVerificationChallenge> findActive(AccountId accountId, Instant now, boolean lock) {
      return items.stream()
          .filter(c -> c.accountId().equals(accountId))
          .filter(c -> c.status() == PhoneVerificationStatus.sent && c.expiresAt().isAfter(now))
          .max(Comparator.comparing(PhoneVerificationChallenge::createdAt));
    }

    @Override public Optional<PhoneVerificationChallenge> findById(String id) {
      return items.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    @Override public Optional<PhoneVerificationChallenge> findLatestForAccount(AccountId id) {
      return items.stream().filter(c -> c.accountId().equals(id))
          .max(Comparator.comparing(PhoneVerificationChallenge::createdAt));
    }

    @Override public long countCreatedForAccountSince(AccountId id, Instant since) {
      return items.stream().filter(c -> c.accountId().equals(id) && !c.createdAt().isBefore(since)).count();
    }

    @Override public long countCreatedForPhoneSince(String phone, Instant since) {
      return items.stream().filter(c -> c.phone().equals(phone) && !c.createdAt().isBefore(since)).count();
    }

    @Override public void invalidateActive(AccountId id) {
      for (int i = 0; i < items.size(); i++) {
        PhoneVerificationChallenge challenge = items.get(i);
        if (challenge.accountId().equals(id)
            && (challenge.status() == PhoneVerificationStatus.pending || challenge.status() == PhoneVerificationStatus.sent)) {
          items.set(i, challenge.expired());
        }
      }
    }

    @Override public PhoneVerificationChallenge save(PhoneVerificationChallenge challenge) {
      items.removeIf(existing -> existing.id().equals(challenge.id()));
      items.add(challenge);
      return challenge;
    }

    @Override public void deleteCreatedBefore(Instant before) {
      items.removeIf(challenge -> challenge.createdAt().isBefore(before));
    }
  }
}
