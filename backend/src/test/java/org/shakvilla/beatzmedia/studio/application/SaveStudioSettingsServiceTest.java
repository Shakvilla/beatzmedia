package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.studio.application.port.in.NotificationsView;
import org.shakvilla.beatzmedia.studio.application.port.in.PayoutSettingsView;
import org.shakvilla.beatzmedia.studio.application.port.in.PrivacySettingsView;
import org.shakvilla.beatzmedia.studio.application.port.in.SaveStudioSettingsCommand;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioDefaultsView;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioSettingsView;
import org.shakvilla.beatzmedia.studio.application.port.in.TeamMemberView;
import org.shakvilla.beatzmedia.studio.application.service.SaveStudioSettingsService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.fakes.FakeAccountReader;
import org.shakvilla.beatzmedia.studio.fakes.FakeStudioRepository;

/**
 * Unit tests for {@link SaveStudioSettingsService} — LLFR-STUDIO-04.2 (studio settings save),
 * including the INV-10 audit requirement (mirrors {@code UpdateEpisodeServiceTest}'s pattern).
 */
@Tag("unit")
class SaveStudioSettingsServiceTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

  private FakeStudioRepository repo;
  private FakeAuditWriter auditWriter;
  private SaveStudioSettingsService service;

  @BeforeEach
  void setUp() {
    repo = new FakeStudioRepository();
    auditWriter = new FakeAuditWriter();
    service = new SaveStudioSettingsService(
        repo, FakeAccountReader.of("artist@example.com"), FakeClock.at(NOW), FakeIds.sequential("aud"),
        auditWriter);
  }

  private static SaveStudioSettingsCommand happyPathCommand() {
    return new SaveStudioSettingsCommand(
        new NotificationsView(true, true, false, true, false, false, false),
        new StudioDefaultsView(BigDecimal.valueOf(2.5), "scheduled", true, false),
        new PayoutSettingsView(true, BigDecimal.valueOf(50), "TIN-1"),
        new PrivacySettingsView(true, false, true, true),
        List.of(new TeamMemberView("u1", "Black Sherif", "hello@onepaygh.com", "Owner")));
  }

  @Test
  void save_happyPath_persistsCategoryAAndReturnsFullView() {
    StudioSettingsView view = service.save(ARTIST, happyPathCommand());

    assertTrue(view.notifications().sales());
    assertEquals("scheduled", view.defaults().releaseVisibility());
    assertEquals(BigDecimal.valueOf(250, 2), view.defaults().trackPrice());
    assertEquals("TIN-1", view.payouts().taxId());
    assertEquals(1, view.team().size());
    assertEquals("Owner", view.team().get(0).role());
    // Category B still composed on the response, even though never accepted as input.
    assertEquals("artist@example.com", view.email());
    assertEquals("Free", view.billing().plan());

    assertTrue(repo.findSettings(ARTIST).isPresent());
  }

  @Test
  void save_appendsExactlyOneAuditEntry() {
    service.save(ARTIST, happyPathCommand());

    assertEquals(1, auditWriter.size());
    var entry = auditWriter.all().get(0);
    assertEquals(ARTIST.value(), entry.getActor());
    assertEquals(AuditType.SETTINGS, entry.getType());
    assertEquals("StudioSettings", entry.getTargetType());
    assertEquals(ARTIST.value(), entry.getTargetId());
  }

  @Test
  void save_replay_isNaturalUpsert_stillExactlyOneAuditEntryPerCall() {
    service.save(ARTIST, happyPathCommand());
    service.save(ARTIST, happyPathCommand());

    // Natural upsert: replaying the same request twice does not create a second settings row —
    // but it DOES append an audit entry per call (each PUT is its own privileged mutation, INV-10).
    assertEquals(2, auditWriter.size());
    assertTrue(repo.findSettings(ARTIST).isPresent());
  }

  @Test
  void save_differentArtists_doNotShareSettings() {
    service.save(ARTIST, happyPathCommand());

    ArtistId otherArtist = new ArtistId("artist-2");
    SaveStudioSettingsCommand otherCmd = new SaveStudioSettingsCommand(
        new NotificationsView(false, false, false, false, false, false, false),
        new StudioDefaultsView(BigDecimal.ZERO, "public", false, false),
        new PayoutSettingsView(false, BigDecimal.ZERO, ""),
        new PrivacySettingsView(false, false, false, false),
        List.of());
    service.save(otherArtist, otherCmd);

    assertTrue(repo.findSettings(ARTIST).get().notifications().sales());
    assertTrue(repo.findSettings(otherArtist).isPresent());
    org.junit.jupiter.api.Assertions.assertFalse(
        repo.findSettings(otherArtist).get().notifications().sales());
  }
}
