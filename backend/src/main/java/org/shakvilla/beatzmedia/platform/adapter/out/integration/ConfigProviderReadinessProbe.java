package org.shakvilla.beatzmedia.platform.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.shakvilla.beatzmedia.platform.application.port.in.ProviderRuntimeConfigAccess;
import org.shakvilla.beatzmedia.platform.application.port.out.ProviderReadinessProbe;
import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;

import io.quarkus.runtime.LaunchMode;

/** Checks whether required runtime configuration exists without returning any secret value. */
@ApplicationScoped
public class ConfigProviderReadinessProbe implements ProviderReadinessProbe {
  private final Config config;
  private final ProviderRuntimeConfigAccess runtimeConfig;

  @Inject
  public ConfigProviderReadinessProbe(Config config, ProviderRuntimeConfigAccess runtimeConfig) {
    this.config = config;
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public Readiness check(ProviderCapability capability, ProviderCode provider) {
    if (provider.capability() != capability) {
      return Readiness.unavailable("provider_capability_mismatch");
    }
    return switch (provider) {
      case MINIO -> require("beatz.s3.endpoint", "beatz.s3.access-key", "beatz.s3.secret-key");
      case MEGA_S4 ->
          unreleasedInProduction(
              require(
                  "beatz.s4.endpoint",
                  "beatz.s4.public-endpoint",
                  "beatz.s4.region",
                  "beatz.s4.access-key",
                  "beatz.s4.secret-key",
                  "beatz.s4.bucket-originals",
                  "beatz.s4.bucket-delivery"));
      case SMTP -> require("quarkus.mailer.host", "quarkus.mailer.from");
      case RESEND -> resendReadiness();
      case CAPTURE -> require("beatz.sms.endpoint");
      case FROG -> frogReadiness();
      case SANDBOX ->
          LaunchMode.current() == LaunchMode.NORMAL
              ? Readiness.unavailable("sandbox_forbidden_in_production")
              : Readiness.available();
      case REDDE ->
          require("beatz.redde.api-key", "beatz.redde.app-id");
      case EMAIL_DISABLED, SMS_DISABLED, PAYMENT_DISABLED -> Readiness.available();
    };
  }

  private Readiness resendReadiness() {
    try {
      var stored = runtimeConfig.get(ProviderCapability.EMAIL, ProviderCode.RESEND);
      if (stored.isPresent()) {
        var values = stored.get().values();
        return requireValues(values.get("apiKey"), values.get("from"));
      }
      return require("beatz.resend.api-key", "beatz.resend.from");
    } catch (RuntimeException e) {
      return Readiness.unavailable("provider_configuration_unavailable");
    }
  }

  private Readiness frogReadiness() {
    try {
      var stored = runtimeConfig.get(ProviderCapability.SMS, ProviderCode.FROG);
      if (stored.isPresent()) {
        var values = stored.get().values();
        return requireValues(values.get("apiKey"), values.get("username"), values.get("senderId"));
      }
      return require("beatz.frog.base-url", "beatz.frog.api-key", "beatz.frog.username", "beatz.frog.sender-id");
    } catch (RuntimeException e) {
      return Readiness.unavailable("provider_configuration_unavailable");
    }
  }

  private Readiness requireValues(String... values) {
    for (String value : values) {
      if (value == null || value.isBlank()) return Readiness.unavailable("missing_configuration");
    }
    return Readiness.available();
  }

  private Readiness require(String... keys) {
    for (String key : keys) {
      if (config.getOptionalValue(key, String.class).map(String::trim).filter(v -> !v.isEmpty()).isEmpty()) {
        return Readiness.unavailable("missing_configuration:" + key);
      }
    }
    return Readiness.available();
  }

  private Readiness unreleasedInProduction(Readiness configured) {
    if (LaunchMode.current() == LaunchMode.NORMAL) {
      return Readiness.unavailable("provider_adapter_not_released");
    }
    return configured;
  }
}
