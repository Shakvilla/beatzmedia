package org.shakvilla.beatzmedia.platform.adapter.out.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ProductionConfigurationGuardTest {

  private static final String SECRET = "must-not-appear-in-errors";

  @Test
  void rejectsMissingProductionPropertiesWithoutLoggingValues() {
    Map<String, String> values = completeConfiguration();
    values.remove("beatz.s3.secret-key");
    values.put("beatz.payment.webhook-secret", "  ");

    ProductionConfigurationGuard guard = new ProductionConfigurationGuard(null);

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> guard.verify(true, key -> Optional.ofNullable(values.get(key))));

    assertTrue(failure.getMessage().contains("beatz.s3.secret-key"));
    assertTrue(failure.getMessage().contains("beatz.payment.webhook-secret"));
    assertFalse(failure.getMessage().contains(SECRET));
  }

  @Test
  void acceptsCompleteProductionConfiguration() {
    Map<String, String> values = completeConfiguration();
    ProductionConfigurationGuard guard = new ProductionConfigurationGuard(null);

    assertDoesNotThrow(() -> guard.verify(true, key -> Optional.ofNullable(values.get(key))));
  }

  @Test
  void preservesDevelopmentAndTestDefaults() {
    ProductionConfigurationGuard guard = new ProductionConfigurationGuard(null);

    assertDoesNotThrow(() -> guard.verify(false, key -> Optional.empty()));
  }

  @Test
  void treatsUnresolvedExpressionsAsMissing() {
    ProductionConfigurationGuard guard = new ProductionConfigurationGuard(null);

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                guard.verify(
                    true,
                    key -> {
                      throw new IllegalArgumentException("unresolved " + SECRET);
                    }));

    assertTrue(failure.getMessage().contains("quarkus.http.cors.origins"));
    assertFalse(failure.getMessage().contains(SECRET));
  }

  private static Map<String, String> completeConfiguration() {
    Map<String, String> values = new HashMap<>();
    ProductionConfigurationGuard.REQUIRED_PROPERTIES.forEach(key -> values.put(key, SECRET));
    assertEquals(ProductionConfigurationGuard.REQUIRED_PROPERTIES.size(), values.size());
    return values;
  }
}
