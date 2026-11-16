package org.shakvilla.beatzmedia.notifications.adapter.out.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.notifications.application.port.out.EmailMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.Mailer;
import org.shakvilla.beatzmedia.notifications.domain.PermanentDeliveryException;
import org.shakvilla.beatzmedia.notifications.domain.TransientDeliveryException;
import org.shakvilla.beatzmedia.platform.application.port.in.ProviderRuntimeConfigAccess;
import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Resend email transport. It records acceptance by HTTP response; delivery status is separate. */
@ApplicationScoped
@EmailProvider("resend")
public class ResendMailer implements Mailer {
  private static final Logger LOG = Logger.getLogger(ResendMailer.class);
  private static final Pattern SAFE_ERROR_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  private final HttpClient client;
  private final ObjectMapper json;
  private final String environmentApiKey;
  private final String environmentFrom;
  private final URI endpoint;
  private final ProviderRuntimeConfigAccess runtimeConfig;

  @Inject
  public ResendMailer(
      ObjectMapper json,
      @ConfigProperty(name = "beatz.resend.api-key") java.util.Optional<String> apiKey,
      @ConfigProperty(name = "beatz.resend.from") java.util.Optional<String> from,
      @ConfigProperty(name = "beatz.resend.endpoint", defaultValue = "https://api.resend.com/emails")
          String endpoint,
      ProviderRuntimeConfigAccess runtimeConfig) {
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.json = json;
    this.environmentApiKey = apiKey.orElse("");
    this.environmentFrom = from.orElse("");
    this.endpoint = URI.create(endpoint);
    this.runtimeConfig = runtimeConfig;
  }

  ResendMailer(
      HttpClient client,
      ObjectMapper json,
      String apiKey,
      String from,
      URI endpoint,
      ProviderRuntimeConfigAccess runtimeConfig) {
    this.client = client;
    this.json = json;
    this.environmentApiKey = apiKey;
    this.environmentFrom = from;
    this.endpoint = endpoint;
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void send(EmailMessage message) {
    sendTracked(message, "resend");
  }

  @Override
  public String sendTracked(EmailMessage message, String provider) {
    ResendSettings settings = settings();
    if (settings.apiKey().isBlank() || settings.from().isBlank()) {
      throw new TransientDeliveryException("Resend is not configured");
    }
    if (message.idempotencyKey() == null || message.idempotencyKey().isBlank()
        || message.idempotencyKey().length() > 256) {
      throw new PermanentDeliveryException("invalid email idempotency key");
    }
    try {
      Map<String, Object> requestBody = new LinkedHashMap<>();
      requestBody.put("from", settings.from());
      requestBody.put("to", List.of(message.to()));
      requestBody.put("subject", message.subject());
      requestBody.put("text", message.body());
      if (message.htmlBody() != null && !message.htmlBody().isBlank()) {
        requestBody.put("html", message.htmlBody());
      }
      byte[] payload = json.writeValueAsBytes(requestBody);
      HttpRequest request =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + settings.apiKey())
              .header("Content-Type", "application/json")
              .header("Idempotency-Key", message.idempotencyKey())
              .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status >= 200 && status < 300) {
        try {
          String messageId = json.readTree(response.body()).path("id").asText(null);
          LOG.infof("Resend accepted transactional email (http=%d)", status);
          return messageId;
        } catch (IOException e) {
          throw new TransientDeliveryException("Resend returned an unreadable response", e);
        }
      }
      if (status == 429 || status >= 500) {
        LOG.warnf("Resend temporarily rejected transactional email (http=%d, category=%s)",
            status, responseCategory(response.body()));
        throw new TransientDeliveryException("Resend temporarily rejected the email");
      }
      LOG.errorf("Resend rejected transactional email (http=%d, category=%s)",
          status, responseCategory(response.body()));
      throw new PermanentDeliveryException("Resend rejected the email request");
    } catch (IOException e) {
      LOG.errorf("Resend request failed before receiving an HTTP response (%s)", e.getClass().getSimpleName());
      throw new TransientDeliveryException("Resend request failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Resend request was interrupted before receiving a response");
      throw new TransientDeliveryException("Resend request interrupted", e);
    }
  }

  private ResendSettings settings() {
    try {
      var stored = runtimeConfig.get(ProviderCapability.EMAIL, ProviderCode.RESEND);
      if (stored.isPresent()) {
        var values = stored.get().values();
        return new ResendSettings(values.getOrDefault("apiKey", ""), values.getOrDefault("from", ""));
      }
      return new ResendSettings(environmentApiKey, environmentFrom);
    } catch (RuntimeException e) {
      throw new TransientDeliveryException("Resend configuration is unavailable", e);
    }
  }

  private String responseCategory(String responseBody) {
    try {
      var response = json.readTree(responseBody);
      String errorName = response.path("name").asText("");
      String message = response.path("message").asText("").toLowerCase(Locale.ROOT);
      if (message.contains("domain") && (message.contains("verif") || message.contains("dns"))) {
        return "sender_domain_verification";
      }
      if (message.contains("resend.dev") || message.contains("testing email")) {
        return "testing_domain_recipient_restriction";
      }
      if (message.contains("from") || message.contains("sender")) {
        return "sender_address_validation";
      }
      if (message.contains("recipient") || message.contains("email address")) {
        return "recipient_validation";
      }
      if (SAFE_ERROR_NAME.matcher(errorName).matches()) return errorName;
    } catch (IOException ignored) {
      // Response contents are intentionally not logged; callers get only a safe category.
    }
    return "unknown_provider_error";
  }

  private record ResendSettings(String apiKey, String from) {}
}
