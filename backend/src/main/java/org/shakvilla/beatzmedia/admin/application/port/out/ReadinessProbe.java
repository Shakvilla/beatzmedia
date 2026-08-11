package org.shakvilla.beatzmedia.admin.application.port.out;

import java.util.List;

/**
 * Outbound port: the platform's own readiness checks, as the admin console needs to read them.
 *
 * <p><strong>Why this exists (GAP-04).</strong> {@code GetHealthService} returned a literal:
 *
 * <pre>return new HealthView("normal", List.of(), List.of(), List.of());</pre>
 *
 * <p>So {@code /admin/health} showed a green "All systems normal" whose only real content was that
 * the endpoint had answered. It would have read exactly the same during a total database or payment
 * outage — worse than having no health page, because it manufactures confidence.
 *
 * <p>The signal was already there and unread: the build carries {@code quarkus-smallrye-health}, and
 * Quarkus registers a datasource readiness check on its own. This port exposes those checks to the
 * application layer without dragging SmallRye's types across the boundary.
 *
 * <p><strong>An empty list means "nothing is being measured", not "everything is fine."</strong>
 * That distinction is the whole point of the gap, so it is stated here rather than left to each
 * caller to remember.
 */
public interface ReadinessProbe {

  /**
   * Every registered readiness check and its current state. Empty when no checks are registered at
   * all — callers must not read that as healthy.
   */
  List<Check> checks();

  /**
   * @param name the check's registered name, e.g. {@code "Database connections health check"}
   * @param up whether the check currently passes
   * @param detail human-readable context from the check's own data, or {@code null} when it
   *     reported none. Never invented — a check that says nothing shows nothing.
   */
  record Check(String name, boolean up, String detail) {}
}
