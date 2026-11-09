package org.shakvilla.beatzmedia.notifications.adapter.out.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.notifications.application.port.out.SmsMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.SmsSender;
import org.shakvilla.beatzmedia.notifications.domain.PermanentDeliveryException;
import org.shakvilla.beatzmedia.notifications.domain.TransientDeliveryException;
import org.shakvilla.beatzmedia.platform.application.port.in.ProviderRuntimeConfigAccess;
import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** FROG SMS transport. An HTTP success is accepted only when FROG returns status {@code ACCEPTD}. */
@ApplicationScoped
@SmsProvider("frog")
public class FrogSmsSender implements SmsSender {

  private static final Logger LOG = Logger.getLogger(FrogSmsSender.class);
  private static final String SEND_PATH = "/api/v3/sms/send";

  private final HttpClient client;
  private final ObjectMapper json;
  private final ProviderRuntimeConfigAccess runtimeConfig;
  private final Config config;
  private final String baseUrl;

  @Inject
  public FrogSmsSender(
      ProviderRuntimeConfigAccess runtimeConfig,
      Config config,
      ObjectMapper json,
      @ConfigProperty(name = "beatz.frog.base-url") String baseUrl) {
    this(runtimeConfig, config, json, baseUrl,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  }

  FrogSmsSender(
      ProviderRuntimeConfigAccess runtimeConfig,
      Config config,
      ObjectMapper json,
      String baseUrl,
      HttpClient client) {
    this.runtimeConfig = runtimeConfig;
    this.config = config;
    this.json = json;
    this.baseUrl = baseUrl;
    this.client = client;
  }

  @Override
  public void send(SmsMessage message) {
    send(message, "frog");
  }

  @Override
  public void send(SmsMessage message, String provider) {
    if (!"frog".equals(provider)) {
      throw new PermanentDeliveryException("invalid FROG provider selection");
    }
    FrogConfig settings = settings();
    String body;
    try {
      String msgId = providerMessageId(message.idempotencyKey());
      Map<String, Object> payload = Map.of(
          "senderid", settings.senderId(),
          "destinations", new Object[] {Map.of(
              "destination", frogDestination(message.to()),
              "msgid", msgId)} ,
          "message", message.body(),
          "smstype", "text");
      body = json.writeValueAsString(payload);
    } catch (Exception e) {
      throw new PermanentDeliveryException("invalid SMS message");
    }

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl.replaceAll("/+$", "") + SEND_PATH))
        .timeout(Duration.ofSeconds(15))
        .header("Content-Type", "application/json")
        .header("API-KEY", settings.apiKey())
        .header("USERNAME", settings.username())
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("FROG SMS request interrupted");
      throw new TransientDeliveryException("FROG SMS request interrupted", e);
    } catch (Exception e) {
      LOG.warn("FROG SMS request failed before receiving a response");
      throw new TransientDeliveryException("FROG SMS request failed", e);
    }

    int status = response.statusCode();
    if (status == 429 || status >= 500) {
      LOG.warnf("FROG SMS returned retryable HTTP status %d", status);
      throw new TransientDeliveryException("FROG SMS provider temporarily unavailable");
    }
    if (status < 200 || status >= 300) {
      LOG.warnf("FROG SMS rejected request with HTTP status %d", status);
      throw new PermanentDeliveryException("FROG SMS provider rejected request");
    }

    try {
      JsonNode responseBody = json.readTree(response.body());
      if ("ACCEPTD".equalsIgnoreCase(responseBody.path("status").asText())) {
        LOG.info("FROG accepted SMS for processing");
        return;
      }
      String responseStatus = responseBody.path("status").asText();
      if ("ERROR".equalsIgnoreCase(responseStatus)) {
        String responseMessage = responseBody.path("message").asText("").toLowerCase();
        if (responseMessage.contains("temporar") || responseMessage.contains("try again")) {
          throw new TransientDeliveryException("FROG SMS was not accepted");
        }
      }
      LOG.warn("FROG did not accept SMS for processing");
      throw new PermanentDeliveryException("FROG SMS was not accepted");
    } catch (TransientDeliveryException | PermanentDeliveryException e) {
      throw e;
    } catch (Exception e) {
      LOG.warn("FROG SMS response could not be parsed");
      throw new TransientDeliveryException("FROG SMS response was unreadable", e);
    }
  }

  private FrogConfig settings() {
    try {
      Optional<Map<String, String>> stored = runtimeConfig
          .get(ProviderCapability.SMS, ProviderCode.FROG)
          .map(runtime -> runtime.values());
      Map<String, String> values = stored.orElse(Map.of());
      String apiKey = value(values, "apiKey", "beatz.frog.api-key");
      String username = value(values, "username", "beatz.frog.username");
      String senderId = value(values, "senderId", "beatz.frog.sender-id");
      if (apiKey.isBlank() || username.isBlank() || senderId.isBlank()) {
        throw new TransientDeliveryException("FROG SMS is not configured");
      }
      return new FrogConfig(apiKey, username, senderId);
    } catch (TransientDeliveryException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new TransientDeliveryException("FROG SMS configuration is unavailable", e);
    }
  }

  private String configured(String key) {
    if (config == null) return "";
    return config.getOptionalValue(key, String.class).orElse("").trim();
  }

  private String value(Map<String, String> values, String key, String property) {
    String stored = values.get(key);
    return stored == null || stored.isBlank() ? configured(property) : stored.trim();
  }

  private static String providerMessageId(String key) {
    String source = key == null ? "sms" : key;
    String normalized = source.replaceAll("[^A-Za-z0-9]", "");
    if (normalized.isBlank()) normalized = "SMS";
    return normalized.length() > 32 ? normalized.substring(normalized.length() - 32) : normalized;
  }

  /** FROG's published Ghana examples use the national trunk-prefixed number form. */
  private static String frogDestination(String value) {
    String digits = value == null ? "" : value.replaceAll("\\D", "");
    if (digits.startsWith("233") && digits.length() == 12) return "0" + digits.substring(3);
    if (digits.length() == 9) return "0" + digits;
    if (digits.length() == 10 && digits.startsWith("0")) return digits;
    if (digits.length() >= 7 && digits.length() <= 15) return digits;
    throw new PermanentDeliveryException("invalid SMS destination format");
  }

  private record FrogConfig(String apiKey, String username, String senderId) {}
}
