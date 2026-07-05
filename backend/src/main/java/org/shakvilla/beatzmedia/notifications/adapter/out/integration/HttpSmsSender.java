package org.shakvilla.beatzmedia.notifications.adapter.out.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.notifications.application.port.out.SmsMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.SmsSender;
import org.shakvilla.beatzmedia.notifications.domain.PermanentDeliveryException;
import org.shakvilla.beatzmedia.notifications.domain.TransientDeliveryException;

/**
 * {@link SmsSender} implementation — a small dependency-free HTTP POST against {@code
 * beatz.sms.endpoint}. Dev/test points this at the in-repo SMS capture stub ({@code sms:8026}, no
 * real SMS provider call — OQ-9); prod points it at a real provider's HTTP endpoint via config
 * (secrets supplied at deploy time, never in code — human deploy gate). Notifications ADD §5.2.
 *
 * <p><strong>Failure classification.</strong> A 2xx response is success; a 429 or 5xx is treated
 * as {@link TransientDeliveryException} (retryable — rate-limited or a transient provider/network
 * issue); any other 4xx (e.g. 400 invalid number, 401/403 auth) is {@link
 * PermanentDeliveryException} — retrying an invalid request would never succeed. A network-level
 * failure (timeout, connection refused) is also transient.
 *
 * <p><strong>No PII in logs.</strong> The recipient phone number is never logged.
 */
@ApplicationScoped
public class HttpSmsSender implements SmsSender {

  private static final Logger LOG = Logger.getLogger(HttpSmsSender.class);

  private final HttpClient client;
  private final String endpoint;
  private final String apiKey;

  @Inject
  public HttpSmsSender(
      @ConfigProperty(name = "beatz.sms.endpoint") String endpoint,
      @ConfigProperty(name = "beatz.sms.api-key") String apiKey) {
    this.endpoint = endpoint;
    this.apiKey = apiKey;
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  @Override
  public void send(SmsMessage message) {
    String json =
        "{\"to\":" + jsonString(message.to())
            + ",\"body\":" + jsonString(message.body())
            + ",\"idempotencyKey\":" + jsonString(message.idempotencyKey())
            + "}";

    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));
    if (apiKey != null && !apiKey.isBlank()) {
      builder.header("Authorization", "Bearer " + apiKey);
    }

    HttpResponse<String> response;
    try {
      response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (Exception e) {
      LOG.warn("sms dispatch failed to reach endpoint; classified as transient (eligible for retry)");
      throw new TransientDeliveryException("sms send failed: network error", e);
    }

    int status = response.statusCode();
    if (status >= 200 && status < 300) {
      LOG.info("sms dispatched successfully");
      return;
    }
    if (status == 429 || status >= 500) {
      LOG.warnf("sms dispatch failed with status %d; classified as transient", status);
      throw new TransientDeliveryException("sms send failed: status " + status);
    }
    LOG.warnf("sms dispatch failed with status %d; classified as permanent", status);
    throw new PermanentDeliveryException("sms send failed: status " + status);
  }

  private static String jsonString(String value) {
    if (value == null) {
      return "null";
    }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
