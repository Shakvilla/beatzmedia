package org.shakvilla.beatzmedia.platform.adapter.out.config;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;

/** Fails production startup before traffic is served when mandatory secrets are unavailable. */
@ApplicationScoped
public class ProductionConfigurationGuard {

  static final List<String> REQUIRED_PROPERTIES =
      List.of(
          "quarkus.http.cors.origins",
          "quarkus.datasource.password",
          "beatz.s3.access-key",
          "beatz.s3.secret-key",
          "mp.jwt.verify.publickey.location",
          "smallrye.jwt.sign.key.location",
          "beatz.payment.webhook-secret",
          "beatz.provider-config.encryption-key",
          "beatz.phone-verification.hmac-key");

  private static final Logger LOG = Logger.getLogger(ProductionConfigurationGuard.class);

  private final Config config;

  @Inject
  public ProductionConfigurationGuard(Config config) {
    this.config = config;
  }

  void onStart(@Observes StartupEvent event) {
    verify(LaunchMode.current() == LaunchMode.NORMAL, this::read);
  }

  void verify(boolean production, Function<String, Optional<String>> propertyLookup) {
    if (!production) {
      return;
    }

    List<String> missing =
        REQUIRED_PROPERTIES.stream()
            .filter(property -> isMissing(property, propertyLookup))
            .toList();

    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Production configuration is incomplete; missing required properties: "
              + String.join(", ", missing));
    }

    LOG.infof("Production configuration validated: %d required properties present", REQUIRED_PROPERTIES.size());
  }

  private Optional<String> read(String property) {
    return config.getOptionalValue(property, String.class);
  }

  private static boolean isMissing(
      String property, Function<String, Optional<String>> propertyLookup) {
    try {
      return propertyLookup.apply(property).map(String::isBlank).orElse(true);
    } catch (RuntimeException unresolvedExpression) {
      return true;
    }
  }
}
