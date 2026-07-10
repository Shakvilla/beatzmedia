package org.shakvilla.beatzmedia.admin.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.admin.application.port.in.GetHealth;
import org.shakvilla.beatzmedia.admin.application.port.in.HealthView;

/**
 * Application service for LLFR-ADMIN-01.2 (health). Read-only; not audited.
 *
 * <p><strong>Entirely a Category B honest static default.</strong> There is no APM/observability
 * pipeline, incident-tracking system, payment-gateway health monitor, or concurrent-listener
 * telemetry anywhere in this codebase — {@code metrics}/{@code listeners}/{@code incidents} are
 * always empty, never fabricated. {@code status} is hardcoded {@code "normal"}: the one honest
 * signal available is that this endpoint answering an HTTP request at all means the app is up; there
 * is no failure-detection logic to ever honestly return {@code "degraded"}. See {@link HealthView}
 * and admin ADD §13 as-built for the full rationale.
 */
@ApplicationScoped
public class GetHealthService implements GetHealth {

  @Override
  public HealthView health() {
    return new HealthView("normal", List.of(), List.of(), List.of());
  }
}
