package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.List;

/**
 * Wire-shaped view for {@code GET /admin/health}, matching {@code Health} in {@code
 * Frontend/src/lib/admin-data.ts}. Admin ADD §6 / §16 (WU-ADM-1).
 *
 * <p><strong>Almost entirely a placeholder (Category B, honest-empty).</strong> There is no APM/
 * observability pipeline, incident-tracking system, payment-gateway health monitor, or
 * concurrent-listener telemetry anywhere in this codebase. {@code metrics}/{@code listeners}/{@code
 * incidents} are always empty arrays — never fabricated entries (no {@code "API p95"}, {@code
 * "Streaming uptime"}, {@code "MoMo gateway"}, {@code "CDN errors"} values, no incident history).
 * {@code status} is hardcoded {@code "normal"}: the one honest signal available is that if this
 * endpoint answers an HTTP request at all, the app is up — there is no failure-detection logic to
 * ever honestly return {@code "degraded"}. Real observability infrastructure is a future WU, not
 * invented here (admin ADD §16 as-built).
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
