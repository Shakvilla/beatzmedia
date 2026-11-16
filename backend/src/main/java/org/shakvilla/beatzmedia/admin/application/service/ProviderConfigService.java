package org.shakvilla.beatzmedia.admin.application.service;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.Config;
import org.shakvilla.beatzmedia.admin.application.port.in.ManageProviderConfig;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.in.ManageProviderSelections;
import org.shakvilla.beatzmedia.platform.application.port.in.ManageProviderSelections.ProviderSelectionView;
import org.shakvilla.beatzmedia.platform.application.port.in.ProviderRuntimeConfigAccess;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;
import org.shakvilla.beatzmedia.platform.domain.ProviderRuntimeConfig;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

@ApplicationScoped
public class ProviderConfigService implements ManageProviderConfig {
  private final ManageProviderSelections selections;
  private final AuditWriter auditWriter;
  private final IdGenerator ids;
  private final Clock clock;
  private final ProviderRuntimeConfigAccess runtimeConfig;
  private final Config config;

  @Inject
  public ProviderConfigService(
      ManageProviderSelections selections,
      AuditWriter auditWriter,
      IdGenerator ids,
      Clock clock,
      ProviderRuntimeConfigAccess runtimeConfig,
      Config config) {
    this.selections = selections;
    this.auditWriter = auditWriter;
    this.ids = ids;
    this.clock = clock;
    this.runtimeConfig = runtimeConfig;
    this.config = config;
  }

  /** Constructor retained as a narrow test seam for selection-only tests. */
  public ProviderConfigService(
      ManageProviderSelections selections, AuditWriter auditWriter, IdGenerator ids, Clock clock) {
    this(selections, auditWriter, ids, clock, null, null);
  }

  @Override
  public List<ProviderSelectionView> list() {
    return selections.list();
  }

  @Override
  @Transactional
  public ProviderSelectionView change(
      String actorId, String capability, String provider, long expectedVersion) {
    ProviderSelectionView saved = selections.change(actorId, capability, provider, expectedVersion);
    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            actorId,
            "Changed provider selection",
            "ProviderSelection",
            saved.capability(),
            AuditType.SETTINGS,
            "provider=" + saved.provider() + "; version=" + saved.version(),
            clock.now()));
    return saved;
  }

  @Override
  public ResendConfigView resendConfig() {
    if (runtimeConfig == null || config == null) {
      throw new IllegalStateException("Provider runtime configuration access is unavailable");
    }
    boolean encryptionReady = runtimeConfig.encryptionAvailable();
    try {
      var stored = runtimeConfig.get(ProviderCapability.EMAIL, ProviderCode.RESEND);
      if (stored.isPresent()) return toResendView(stored.get(), "database", encryptionReady);
    } catch (RuntimeException e) {
      return new ResendConfigView(false, "", "unavailable", 0, null, null, encryptionReady);
    }
    String envKey = config.getOptionalValue("beatz.resend.api-key", String.class).orElse("");
    String envFrom = config.getOptionalValue("beatz.resend.from", String.class).orElse("");
    boolean envConfigured = !envKey.isBlank() && !envFrom.isBlank();
    return new ResendConfigView(
        envConfigured, envFrom, envConfigured ? "environment" : "not-configured", 0, null, null, encryptionReady);
  }

  @Override
  @Transactional
  public ResendConfigView saveResendConfig(
      String actorId, String apiKey, String from, long expectedVersion) {
    if (runtimeConfig == null || config == null) {
      throw new IllegalStateException("Provider runtime configuration access is unavailable");
    }
    if (actorId == null || actorId.isBlank()) {
      throw new ValidationException("actorId is required", "actorId");
    }
    if (expectedVersion < 0) {
      throw new ValidationException("expectedVersion must not be negative", "expectedVersion");
    }

    ProviderRuntimeConfig current;
    try {
      current = runtimeConfig.get(ProviderCapability.EMAIL, ProviderCode.RESEND).orElse(null);
    } catch (RuntimeException e) {
      throw new ValidationException("Stored Resend settings cannot be decrypted", "configuration");
    }
    long currentVersion = current == null ? 0 : current.version();
    if (currentVersion != expectedVersion) {
      throw new ConflictException("Resend configuration changed; reload and retry");
    }

    String currentKey = current == null
        ? config.getOptionalValue("beatz.resend.api-key", String.class).orElse("")
        : current.values().getOrDefault("apiKey", "");
    String currentFrom = current == null
        ? config.getOptionalValue("beatz.resend.from", String.class).orElse("")
        : current.values().getOrDefault("from", "");
    String nextKey = apiKey == null || apiKey.isBlank() ? currentKey : apiKey.trim();
    String nextFrom = from == null || from.isBlank() ? currentFrom : from.trim();
    if (nextFrom.isBlank() || nextFrom.length() > 320 || nextFrom.contains("\n") || nextFrom.contains("\r")) {
      throw new ValidationException("A valid sender address is required", "from");
    }
    if (nextKey.isBlank()) {
      throw new ValidationException("A Resend API key is required", "apiKey");
    }
    if (nextKey.length() > 512 || nextKey.contains("\n") || nextKey.contains("\r")) {
      throw new ValidationException("The Resend API key is invalid", "apiKey");
    }

    var saved = runtimeConfig.save(
        ProviderCapability.EMAIL,
        ProviderCode.RESEND,
        Map.of("apiKey", nextKey, "from", nextFrom),
        expectedVersion,
        actorId);
    ProviderRuntimeConfig config = saved.orElseThrow(
        () -> new ConflictException("Resend configuration changed; reload and retry"));
    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            actorId,
            "Updated Resend email configuration",
            "ProviderRuntimeConfig",
            "email:resend",
            AuditType.SETTINGS,
            "apiKeyUpdated=" + (apiKey != null && !apiKey.isBlank()) + "; senderUpdated=" + (from != null && !from.isBlank()),
            clock.now()));
    return toResendView(config, "database", runtimeConfig.encryptionAvailable());
  }

  @Override
  public FrogConfigView frogConfig() {
    if (runtimeConfig == null || config == null) {
      throw new IllegalStateException("Provider runtime configuration access is unavailable");
    }
    boolean encryptionReady = runtimeConfig.encryptionAvailable();
    try {
      var stored = runtimeConfig.get(ProviderCapability.SMS, ProviderCode.FROG);
      if (stored.isPresent()) return toFrogView(stored.get(), "database", encryptionReady);
    } catch (RuntimeException e) {
      return new FrogConfigView(false, "", "unavailable", 0, null, null, encryptionReady);
    }
    String apiKey = config.getOptionalValue("beatz.frog.api-key", String.class).orElse("");
    String username = config.getOptionalValue("beatz.frog.username", String.class).orElse("");
    String senderId = config.getOptionalValue("beatz.frog.sender-id", String.class).orElse("");
    boolean configured = !apiKey.isBlank() && !username.isBlank() && !senderId.isBlank();
    return new FrogConfigView(
        configured, senderId, configured ? "environment" : "not-configured", 0, null, null, encryptionReady);
  }

  @Override
  @Transactional
  public FrogConfigView saveFrogConfig(
      String actorId, String apiKey, String username, String senderId, long expectedVersion) {
    if (runtimeConfig == null || config == null) {
      throw new IllegalStateException("Provider runtime configuration access is unavailable");
    }
    if (actorId == null || actorId.isBlank()) {
      throw new ValidationException("actorId is required", "actorId");
    }
    if (expectedVersion < 0) {
      throw new ValidationException("expectedVersion must not be negative", "expectedVersion");
    }

    ProviderRuntimeConfig current;
    try {
      current = runtimeConfig.get(ProviderCapability.SMS, ProviderCode.FROG).orElse(null);
    } catch (RuntimeException e) {
      throw new ValidationException("Stored FROG settings cannot be decrypted", "configuration");
    }
    long currentVersion = current == null ? 0 : current.version();
    if (currentVersion != expectedVersion) {
      throw new ConflictException("FROG configuration changed; reload and retry");
    }

    Map<String, String> currentValues = current == null ? Map.of() : current.values();
    String nextKey = nonBlankOr(apiKey, currentValues.getOrDefault(
        "apiKey", config.getOptionalValue("beatz.frog.api-key", String.class).orElse("")));
    String nextUsername = nonBlankOr(username, currentValues.getOrDefault(
        "username", config.getOptionalValue("beatz.frog.username", String.class).orElse("")));
    String nextSender = nonBlankOr(senderId, currentValues.getOrDefault(
        "senderId", config.getOptionalValue("beatz.frog.sender-id", String.class).orElse("")));
    validateFrogValue(nextKey, 512, "apiKey", "A valid FROG API key is required");
    validateFrogValue(nextUsername, 200, "username", "A valid FROG username is required");
    validateFrogValue(nextSender, 50, "senderId", "An approved FROG sender ID is required");

    var saved = runtimeConfig.save(
        ProviderCapability.SMS,
        ProviderCode.FROG,
        Map.of("apiKey", nextKey, "username", nextUsername, "senderId", nextSender),
        expectedVersion,
        actorId);
    ProviderRuntimeConfig savedConfig = saved.orElseThrow(
        () -> new ConflictException("FROG configuration changed; reload and retry"));
    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            actorId,
            "Updated FROG SMS configuration",
            "ProviderRuntimeConfig",
            "sms:frog",
            AuditType.SETTINGS,
            "apiKeyUpdated=" + (apiKey != null && !apiKey.isBlank())
                + "; usernameUpdated=" + (username != null && !username.isBlank())
                + "; senderIdUpdated=" + (senderId != null && !senderId.isBlank()),
            clock.now()));
    return toFrogView(savedConfig, "database", runtimeConfig.encryptionAvailable());
  }

  private static ResendConfigView toResendView(
      ProviderRuntimeConfig config, String source, boolean encryptionReady) {
    String key = config.values().getOrDefault("apiKey", "");
    String from = config.values().getOrDefault("from", "");
    return new ResendConfigView(
        !key.isBlank() && !from.isBlank(),
        from,
        source,
        config.version(),
        config.updatedAt(),
        config.updatedBy(),
        encryptionReady);
  }

  private static FrogConfigView toFrogView(
      ProviderRuntimeConfig config, String source, boolean encryptionReady) {
    Map<String, String> values = config.values();
    return new FrogConfigView(
        !values.getOrDefault("apiKey", "").isBlank()
            && !values.getOrDefault("username", "").isBlank()
            && !values.getOrDefault("senderId", "").isBlank(),
        values.getOrDefault("senderId", ""),
        source,
        config.version(),
        config.updatedAt(),
        config.updatedBy(),
        encryptionReady);
  }

  private static String nonBlankOr(String candidate, String fallback) {
    return candidate == null || candidate.isBlank() ? fallback : candidate.trim();
  }

  private static void validateFrogValue(String value, int maxLength, String field, String message) {
    if (value == null || value.isBlank() || value.length() > maxLength
        || value.contains("\n") || value.contains("\r")) {
      throw new ValidationException(message, field);
    }
  }
}
