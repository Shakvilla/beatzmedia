package org.shakvilla.beatzmedia.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.service.ProviderConfigService;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.platform.application.port.in.ManageProviderSelections;
import org.shakvilla.beatzmedia.platform.application.port.in.ManageProviderSelections.ProviderSelectionView;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

@Tag("unit")
class ProviderConfigServiceTest {
  @Test
  void successfulChangeWritesExactlyOneSettingsAudit() {
    FakeAuditWriter audit = new FakeAuditWriter();
    ProviderConfigService service =
        new ProviderConfigService(
            new StubSelections(), audit, FakeIds.sequential("audit"), FakeClock.fixed());

    service.change("admin-1", "email", "resend", 3);

    assertEquals(1, audit.size());
    assertEquals(AuditType.SETTINGS, audit.all().getFirst().getType());
    assertEquals("ProviderSelection", audit.all().getFirst().getTargetType());
    assertEquals("email", audit.all().getFirst().getTargetId());
  }

  private static final class StubSelections implements ManageProviderSelections {
    @Override
    public List<ProviderSelectionView> list() {
      return List.of();
    }

    @Override
    public ProviderSelectionView change(
        String actorId, String capability, String provider, long expectedVersion) {
      return new ProviderSelectionView(
          capability,
          provider,
          expectedVersion + 1,
          true,
          "ready",
          Instant.parse("2026-01-01T00:00:00Z"),
          actorId);
    }
  }
}
