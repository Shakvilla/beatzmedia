package org.shakvilla.beatzmedia.notifications.adapter.out.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.notifications.application.port.out.SmsMessage;
import org.shakvilla.beatzmedia.notifications.domain.PermanentDeliveryException;
import org.shakvilla.beatzmedia.notifications.domain.TransientDeliveryException;
import org.shakvilla.beatzmedia.platform.application.port.in.ProviderRuntimeConfigAccess;
import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;
import org.shakvilla.beatzmedia.platform.domain.ProviderRuntimeConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@Tag("unit")
class FrogSmsSenderTest {
  private HttpServer server;
  private final AtomicReference<Request> received = new AtomicReference<>();

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  void sendsDocumentedPayloadAndTreatsAcceptedAsQueuedSuccess() throws Exception {
    startServer(200, "{\"status\":\"ACCEPTD\",\"message\":\"Message Accepted For Processing\"}");

    sender().send(new SmsMessage("+233244000111", "Your code is 123456", "verify-abc-123"), "frog");

    Request request = received.get();
    assertEquals("POST", request.method());
    assertEquals("test-api-key", request.apiKey());
    assertEquals("test-user", request.username());
    var payload = new ObjectMapper().readTree(request.body());
    assertEquals("BeatzClik", payload.path("senderid").asText());
    assertEquals("Your code is 123456", payload.path("message").asText());
    assertEquals("text", payload.path("smstype").asText());
    assertEquals("0244000111", payload.path("destinations").get(0).path("destination").asText());
    assertEquals("verifyabc123", payload.path("destinations").get(0).path("msgid").asText());
  }

  @Test
  void rejectsHttpSuccessWhenFrogDoesNotAcceptTheMessage() throws Exception {
    startServer(200, "{\"status\":\"ERROR\",\"message\":\"Invalid phone number format\"}");

    assertThrows(PermanentDeliveryException.class,
        () -> sender().send(new SmsMessage("+233244000111", "test", "id-1"), "frog"));
  }

  @Test
  void retriesServerFailure() throws Exception {
    startServer(503, "{\"status\":\"ERROR\",\"message\":\"Unavailable\"}");

    assertThrows(TransientDeliveryException.class,
        () -> sender().send(new SmsMessage("+233244000111", "test", "id-1"), "frog"));
  }

  private FrogSmsSender sender() {
    ProviderRuntimeConfig runtime = new ProviderRuntimeConfig(
        ProviderCapability.SMS, ProviderCode.FROG,
        Map.of("apiKey", "test-api-key", "username", "test-user", "senderId", "BeatzClik"),
        1, Instant.parse("2026-01-01T00:00:00Z"), "test");
    ProviderRuntimeConfigAccess config = new ProviderRuntimeConfigAccess() {
      @Override
      public Optional<ProviderRuntimeConfig> get(ProviderCapability capability, ProviderCode provider) {
        return Optional.of(runtime);
      }

      @Override
      public Optional<ProviderRuntimeConfig> save(
          ProviderCapability capability, ProviderCode provider, Map<String, String> values,
          long expectedVersion, String updatedBy) {
        return Optional.empty();
      }

      @Override
      public boolean encryptionAvailable() {
        return true;
      }
    };
    return new FrogSmsSender(config, null, new ObjectMapper(),
        "http://127.0.0.1:" + server.getAddress().getPort(), HttpClient.newBuilder().build());
  }

  private void startServer(int status, String responseBody) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v3/sms/send", exchange -> handle(exchange, status, responseBody));
    server.start();
  }

  private void handle(HttpExchange exchange, int status, String responseBody) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    received.set(new Request(exchange.getRequestMethod(),
        exchange.getRequestHeaders().getFirst("API-KEY"),
        exchange.getRequestHeaders().getFirst("USERNAME"), body));
    byte[] response = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, response.length);
    exchange.getResponseBody().write(response);
    exchange.close();
  }

  private record Request(String method, String apiKey, String username, String body) {}
}
