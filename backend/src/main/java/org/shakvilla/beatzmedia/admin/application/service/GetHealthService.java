package org.shakvilla.beatzmedia.admin.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.admin.application.port.in.GetHealth;
import org.shakvilla.beatzmedia.admin.application.port.in.HealthView;
import org.shakvilla.beatzmedia.admin.application.port.out.ReadinessProbe;

/**
 * Application service for LLFR-ADMIN-01.2 (health). Read-only; not audited.
 *
 * <p><strong>GAP-04.</strong> This returned a literal — {@code new HealthView("normal", List.of(),
 * List.of(), List.of())} — so the console showed a green "All systems normal" whose only real
 * content was that the endpoint had answered. It would have read identically during a total
 * database outage.
 *
 * <p>Status is now derived from the platform's own readiness checks, the same ones behind
 * {@code /q/health/ready}. Each check becomes a metric row, so the page states <em>what it
 * measured</em> rather than asserting a mood.
 *
 * <p><strong>Three states, not two.</strong> The old shape could only say normal or degraded, which
 * forced "nothing is being measured" to be reported as one or the other — and it chose healthy.
 * {@code unknown} exists so the page can say so:
 *
 * <ul>
 *   <li>{@code normal} — every registered readiness check passes
 *   <li>{@code degraded} — at least one fails
 *   <li>{@code unknown} — <strong>no checks are registered, or the probe itself failed</strong>
 * </ul>
 *
 * <p>Collapsing that third case back into {@code normal} would reintroduce exactly this gap, so it
 * is a distinct value rather than a default.
 *
 * <p>{@code listeners} and {@code incidents} stay empty and honest: there is still no listener
 * telemetry or incident tracker in this codebase, and this change does not invent one.
 */
@ApplicationScoped
public class GetHealthService implements GetHealth {

  static final String NORMAL = "normal";
  static final String DEGRADED = "degraded";
  static final String UNKNOWN = "unknown";

  private final ReadinessProbe readiness;

  @Inject
  public GetHealthService(ReadinessProbe readiness) {
    this.readiness = readiness;
  }

  @Override
  public HealthView health() {
    List<ReadinessProbe.Check> checks = readiness.checks();

    if (checks.isEmpty()) {
      return new HealthView(UNKNOWN, List.of(), List.of(), List.of());
    }

    String status = checks.stream().allMatch(ReadinessProbe.Check::up) ? NORMAL : DEGRADED;
    List<HealthView.Metric> metrics =
        checks.stream()
            .map(c -> new HealthView.Metric(c.name(), c.up() ? "UP" : "DOWN", detail(c)))
            .toList();

    return new HealthView(status, metrics, List.of(), List.of());
  }

  /**
   * The metric's sub-label. Falls back to naming the source rather than the check's verdict — the
   * value already carries UP/DOWN, and repeating it as prose would pad the card without adding
   * anything.
   */
  private static String detail(ReadinessProbe.Check check) {
    return check.detail() == null || check.detail().isBlank()
        ? "readiness check"
        : check.detail();
  }
}
