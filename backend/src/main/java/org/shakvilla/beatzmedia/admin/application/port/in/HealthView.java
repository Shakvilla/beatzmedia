package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.List;

/**
 * Wire-shaped view for {@code GET /admin/health}, matching {@code Health} in {@code
 * Frontend/src/lib/admin-data.ts}. Admin ADD §6 / §13 (WU-ADM-1).
 *
 * <p><strong>{@code status} and {@code metrics} are real as of GAP-04.</strong> They are derived
 * from the platform's own readiness checks — the same ones behind {@code /q/health/ready} — with one
 * metric row per check. {@code status} is one of:
 *
 * <ul>
 *   <li>{@code "normal"} — every registered readiness check passes
 *   <li>{@code "degraded"} — at least one fails
 *   <li>{@code "unknown"} — no checks are registered, or the probe itself failed
 * </ul>
 *
 * <p>{@code "unknown"} is a distinct value on purpose. The previous two-state shape forced "nothing
 * is being measured" to be reported as either healthy or broken, and it chose healthy: {@code status}
 * was the literal {@code "normal"}, so the console showed a green all-clear that would have read
 * identically during a total database outage.
 *
 * <p><strong>{@code listeners} and {@code incidents} remain honest-empty (Category B).</strong>
 * There is still no concurrent-listener telemetry and no incident tracker in this codebase, so they
 * are always empty arrays — never fabricated entries, no invented incident history. Real
 * observability infrastructure beyond readiness is a future WU (admin ADD §13 as-built).
 */
public record HealthView(String status, List<Metric> metrics, List<Double> listeners, List<Incident> incidents) {

  public HealthView {
    metrics = List.copyOf(metrics);
    listeners = List.copyOf(listeners);
    incidents = List.copyOf(incidents);
  }

  public record Metric(String label, String value, String sub) {}

  public record Incident(String id, String title, String date, String status) {}
}
