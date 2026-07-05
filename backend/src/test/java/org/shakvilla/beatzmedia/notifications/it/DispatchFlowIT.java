package org.shakvilla.beatzmedia.notifications.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.UpdateFanSettings;
import org.shakvilla.beatzmedia.identity.application.port.in.UpdateFanSettings.NotificationPrefs;
import org.shakvilla.beatzmedia.identity.application.port.in.UpdateFanSettings.UpdateFanSettingsCommand;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyCommand;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyUseCase;
import org.shakvilla.beatzmedia.notifications.domain.NotificationType;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end integration test for the WU-NOT-2 dispatch pipeline: real Postgres (notification +
 * delivery_attempt), real CDI event wiring ({@code NotifyService} -> {@code NotificationCreated}
 * -> {@code DispatchSubscriber}), real {@code SmtpMailer} (captured via Quarkus Mailer's {@code
 * MockMailbox} — {@code %test.quarkus.mailer.mock=true}, so no real SMTP/Mailpit connection is
 * required to prove the flow), and a real HTTP POST from {@code HttpSmsSender} to a local embedded
 * server standing in for the SMS capture stub (same contract the real stub serves).
 *
 * <p>Proves the PRD §6.10 acceptance case: given an opted-in recipient, a source event results in
 * BOTH an in-app notification AND a captured email/SMS with a {@code delivery_attempt{status=sent}}
 * row.
 */
@QuarkusTest
@Tag("integration")
class DispatchFlowIT {

  private static HttpServer smsServer;
  private static final List<String> receivedSmsBodies = new ArrayList<>();

  @Inject NotifyUseCase notifyUseCase;
  @Inject AccountRepository accountRepository;
  @Inject UpdateFanSettings updateFanSettings;
  @Inject IdGenerator ids;
  @Inject MockMailbox mailbox;
  @Inject jakarta.persistence.EntityManager em;

  @BeforeAll
  static void startSmsServer() throws IOException {
    // beatz.sms.endpoint defaults to http://localhost:8026/send — bind exactly there so
    // HttpSmsSender (real bean, no test override needed) talks to this embedded stand-in.
    smsServer = HttpServer.create(new InetSocketAddress("localhost", 8026), 0);
    smsServer.createContext(
        "/send",
        (HttpExchange exchange) -> {
          byte[] body = exchange.getRequestBody().readAllBytes();
          receivedSmsBodies.add(new String(body, java.nio.charset.StandardCharsets.UTF_8));
          byte[] response = "{\"status\":\"queued\",\"id\":\"1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    smsServer.start();
  }

  @AfterAll
  static void stopSmsServer() {
    if (smsServer != null) {
      smsServer.stop(0);
    }
  }

  @AfterEach
  void resetCapture() {
    mailbox.clear();
    receivedSmsBodies.clear();
  }

  @jakarta.transaction.Transactional
  void seedFanWithOptIns(AccountId id, String email, String phone) {
    accountRepository.save(
        Account.createFan(id, "Dispatch Fan", email, new Credential(id, "hash"), java.time.Instant.now()));
    updateFanSettings.update(
        id,
        new UpdateFanSettingsCommand(
            java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(new NotificationPrefs(true, true, false)),
            java.util.Optional.empty(), java.util.Optional.of(phone)));
  }

  @jakarta.transaction.Transactional
  void seedFanOptedOut(AccountId id, String email) {
    accountRepository.save(
        Account.createFan(id, "Opted Out Fan", email, new Credential(id, "hash"), java.time.Instant.now()));
    updateFanSettings.update(
        id,
        new UpdateFanSettingsCommand(
            java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(new NotificationPrefs(false, false, false)),
            java.util.Optional.empty(), java.util.Optional.empty()));
  }

  @Test
  void notify_optedInRecipient_dispatchesEmailAndSms_withSentDeliveryAttempts() {
    long n = System.nanoTime();
    AccountId recipient = new AccountId("dispatch-it-" + n);
    String email = "dispatch-it-" + n + "@example.com";
    String phone = "+233555000" + (n % 1000);
    seedFanWithOptIns(recipient, email, phone);

    String dedupeKey = "test-dispatch:" + n;
    notifyUseCase.notify(
        new NotifyCommand(
            dedupeKey, recipient, NotificationType.tip, "You got a tip",
            "You received a tip of ₵5.00", "/studio/payouts"));

    // Dispatch is @Observes(during = AFTER_SUCCESS) — it runs synchronously the moment
    // NotifyService's own @Transactional method commits, i.e. by the time notify() returns above.
    assertEquals(1, mailbox.getMailsSentTo(email).size(), "email must be captured exactly once");
    assertEquals(1, receivedSmsBodies.size(), "sms must be captured exactly once");

    assertTrue(
        mailbox.getMailsSentTo(email).get(0).getText().contains("₵5.00"),
        "email body should carry the rendered notification body");
    assertTrue(
        receivedSmsBodies.get(0).contains("₵5.00"),
        "sms body should carry the rendered notification body (recipient phone travels via the HTTP path, not the JSON body)");

    // delivery_attempt rows exist and are 'sent' for both channels.
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT channel, status FROM delivery_attempt da"
                    + " JOIN notification n ON n.id = da.notification_id"
                    + " WHERE n.dedupe_key = ?1")
            .setParameter(1, dedupeKey)
            .getResultList();
    assertEquals(2, rows.size(), "one delivery_attempt row per dispatched channel");
    for (Object[] row : rows) {
      assertEquals("sent", row[1], "channel " + row[0] + " must be sent");
    }
  }

  @Test
  void notify_optedOutRecipient_noEmailNoSms_noDeliveryAttemptRows() {
    long n = System.nanoTime();
    AccountId recipient = new AccountId("dispatch-optout-it-" + n);
    String email = "dispatch-optout-it-" + n + "@example.com";

    // Defaults would opt the fan INTO newReleases/playlistUpdates, so explicitly opt out of both
    // categories to prove INV-N3 (opted-out ⇒ no DeliveryAttempt at all).
    seedFanOptedOut(recipient, email);

    String dedupeKey = "test-optout:" + n;
    notifyUseCase.notify(
        new NotifyCommand(
            dedupeKey, recipient, NotificationType.tip, "You got a tip",
            "You received a tip of ₵5.00", "/studio/payouts"));

    // Dispatch (AFTER_SUCCESS) has already run synchronously by the time notify() returned.
    assertEquals(0, mailbox.getMailsSentTo(email).size());
    assertTrue(receivedSmsBodies.isEmpty());

    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT channel FROM delivery_attempt da"
                    + " JOIN notification n ON n.id = da.notification_id"
                    + " WHERE n.dedupe_key = ?1")
            .setParameter(1, dedupeKey)
            .getResultList();
    assertTrue(rows.isEmpty(), "INV-N3: no delivery_attempt row at all when opted out");
  }
}
