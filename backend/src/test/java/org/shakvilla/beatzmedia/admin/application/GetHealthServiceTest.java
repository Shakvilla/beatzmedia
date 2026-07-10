package org.shakvilla.beatzmedia.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.in.HealthView;
import org.shakvilla.beatzmedia.admin.application.service.GetHealthService;

/**
 * Unit test for {@link GetHealthService} (LLFR-ADMIN-01.2) — asserts the honest static shape: no
 * fabricated metrics/listeners/incidents. Admin ADD §16 (WU-ADM-1).
 */
@Tag("unit")
class GetHealthServiceTest {

  @Test
  void health_returnsHonestStaticShape_noFabricatedData() {
    HealthView view = new GetHealthService().health();

    assertEquals("normal", view.status());
    assertTrue(view.metrics().isEmpty(), "no APM pipeline exists; never fabricate metrics");
    assertTrue(view.listeners().isEmpty(), "no concurrent-listener telemetry exists");
    assertTrue(view.incidents().isEmpty(), "no incident-tracking system exists");
  }
}
