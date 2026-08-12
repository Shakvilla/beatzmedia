package org.shakvilla.beatzmedia.admin.adapter.out.health;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.admin.application.port.out.ReadinessProbe;

import io.smallrye.health.SmallRyeHealthReporter;

/**
 * Reads the platform's own readiness checks through SmallRye — the same ones behind
 * {@code /q/health/ready}, which the Compose smoke test already gates on.
 *
 * <p>Nothing new is measured here and nothing is invented: this is the signal the app has always
 * published, finally read by the console instead of ignored (GAP-04). Quarkus registers a datasource
 * readiness check on its own, so on this deployment "ready" means at minimum that Postgres answered.
 *
 * <p>The payload is MicroProfile Health's documented shape:
 *
 * <pre>
 * { "status": "UP", "checks": [ { "name": "…", "status": "UP", "data": { … } } ] }
 * </pre>
 */
@ApplicationScoped
public class SmallRyeReadinessProbe implements ReadinessProbe {

  private static final Logger LOG = Logger.getLogger(SmallRyeReadinessProbe.class);

  private final SmallRyeHealthReporter reporter;

  @Inject
  public SmallRyeReadinessProbe(SmallRyeHealthReporter reporter) {
    this.reporter = reporter;
  }

  @Override
  public List<Check> checks() {
    JsonObject payload;
    try {
      payload = reporter.getReadiness().getPayload();
    } catch (RuntimeException e) {
      // A probe that throws must not take the console down with it — but it must not read as
      // healthy either. An empty list is the port's "nothing measured" signal, and GetHealthService
      // reports that as unknown rather than normal.
      LOG.warn("health: readiness probe failed; reporting no checks", e);
      return List.of();
    }

    JsonValue checks = payload.get("checks");
    if (checks == null || checks.getValueType() != JsonValue.ValueType.ARRAY) {
      return List.of();
    }

    List<Check> result = new ArrayList<>();
    for (JsonValue value : checks.asJsonArray()) {
      if (value.getValueType() != JsonValue.ValueType.OBJECT) {
        continue;
      }
      JsonObject check = value.asJsonObject();
      String name = check.getString("name", "unnamed check");
      String status = check.getString("status", "DOWN");
      boolean up = "UP".equals(status);
      result.add(new Check(name, up, detailOf(check, status)));
    }
    return List.copyOf(result);
  }

  /**
   * Flattens a check's own {@code data} object into a short label. Returns {@code null} when the
   * check reported nothing useful — a check that says nothing shows nothing, rather than a
   * fabricated "OK".
   *
   * <p>Entries that merely restate the check's own status are dropped. Quarkus's datasource check
   * reports {@code data: { "<default>": "UP" }}, which would render as the literal
   * {@code "<default>: UP"} beside a value that already says UP — noise that reads like a detail.
   * Genuinely informative data (a {@code reason} on a failure, say) survives.
   */
  private static String detailOf(JsonObject check, String status) {
    JsonValue data = check.get("data");
    if (data == null || data.getValueType() != JsonValue.ValueType.OBJECT) {
      return null;
    }
    Map<String, JsonValue> entries = data.asJsonObject();
    if (entries.isEmpty()) {
      return null;
    }
    String detail = entries.entrySet().stream()
        .filter(e -> !status.equalsIgnoreCase(unquoted(e.getValue())))
        .map(e -> e.getKey() + ": " + unquoted(e.getValue()))
        .collect(Collectors.joining(", "));
    return detail.isBlank() ? null : detail;
  }

  /** {@code JsonValue.toString()} keeps the quotes on strings; the console renders plain text. */
  private static String unquoted(JsonValue value) {
    String raw = value.toString();
    return raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")
        ? raw.substring(1, raw.length() - 1)
        : raw;
  }
}
