package org.shakvilla.beatzmedia.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.in.HealthView;
import org.shakvilla.beatzmedia.admin.application.port.out.ReadinessProbe;
import org.shakvilla.beatzmedia.admin.application.service.GetHealthService;

/**
 * Unit tests for {@link GetHealthService} (LLFR-ADMIN-01.2), rewritten for GAP-04.
 *
 * <p>This previously asserted that the service returned a hardcoded {@code "normal"} with three
 * empty lists — it locked in the very thing the gap report flagged. A green all-clear that would
 * read identically during a total database outage was, as far as the test suite was concerned, the
 * specification.
 *
 * <p>What is tested now is the mapping from readiness checks to the view, and in particular that
 * <strong>absence of measurement is not reported as health</strong>.
 */
@Tag("unit")
class GetHealthServiceTest {

  /** Fake probe — the port exists precisely so this needs no CDI and no SmallRye. */
  private static GetHealthService serviceWith(ReadinessProbe.Check... checks) {
    List<ReadinessProbe.Check> list = List.of(checks);
    return new GetHealthService(() -> list);
  }

  @Test
  void everyCheckPassing_isNormal() {
    HealthView view =
        serviceWith(
                new ReadinessProbe.Check("Database connections health check", true, null),
                new ReadinessProbe.Check("SmallRye Reactive Messaging", true, null))
            .health();

    assertEquals("normal", view.status());
    assertEquals(2, view.metrics().size(), "each check becomes a row, so the page says what it measured");
    assertEquals("UP", view.metrics().get(0).value());
  }

  @Test
  void oneFailingCheck_isDegraded() {
    HealthView view =
        serviceWith(
                new ReadinessProbe.Check("Database connections health check", false, "reason: down"),
                new ReadinessProbe.Check("SmallRye Reactive Messaging", true, null))
            .health();

    assertEquals("degraded", view.status());
    assertEquals("DOWN", view.metrics().get(0).value());
    assertEquals("reason: down", view.metrics().get(0).sub(), "the check's own data, not a rewrite");
  }

  /**
   * The heart of GAP-04. With nothing registered there is no evidence either way, and the old code
   * resolved that to a green all-clear. Reporting {@code normal} here would reintroduce the gap
   * exactly.
   */
  @Test
  void noChecksAtAll_isUnknown_notNormal() {
    HealthView view = serviceWith().health();

    assertEquals("unknown", view.status());
    assertTrue(view.metrics().isEmpty(), "nothing measured means nothing to show");
  }

  @Test
  void listenersAndIncidentsStayHonestlyEmpty() {
    HealthView view =
        serviceWith(new ReadinessProbe.Check("Database connections health check", true, null))
            .health();

    assertTrue(view.listeners().isEmpty(), "no concurrent-listener telemetry exists");
    assertTrue(view.incidents().isEmpty(), "no incident-tracking system exists");
  }

  /** A check that reports no data gets a source label, not an invented "OK". */
  @Test
  void aCheckWithoutDataIsLabelledByItsSource() {
    HealthView view =
        serviceWith(new ReadinessProbe.Check("Database connections health check", true, null))
            .health();

    assertEquals("readiness check", view.metrics().get(0).sub());
  }
}
