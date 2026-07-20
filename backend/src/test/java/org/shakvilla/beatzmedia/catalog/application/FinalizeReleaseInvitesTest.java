package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.catalog.application.service.FinalizeReleaseService;
import org.shakvilla.beatzmedia.catalog.application.service.SplitInviteService;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.SplitConfirmation;
import org.shakvilla.beatzmedia.catalog.domain.SplitEntry;
import org.shakvilla.beatzmedia.catalog.domain.SplitInviteIssued;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.catalog.fakes.RecordingEvent;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Covers the WU-CAT-9 hook in {@link FinalizeReleaseService}: submitting a release with pending
 * collaborator splits mints + persists a {@link org.shakvilla.beatzmedia.catalog.domain.SplitInvite}
 * per collaborator and fires {@link SplitInviteIssued}; a release with no pending splits fires
 * nothing.
 */
@Tag("unit")
class FinalizeReleaseInvitesTest {

  private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");
  private static final ArtistId ARTIST = new ArtistId("artist-1");

  private FakeCatalogRepository repo;
  private RecordingEvent<SplitInviteIssued> invites;
  private FinalizeReleaseService service;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    FakeAuditWriter auditWriter = new FakeAuditWriter();
    FakeClock clock = FakeClock.at(NOW);
    invites = new RecordingEvent<>();
    SplitInviteService splitInviteService = new SplitInviteService(
        repo, clock, FakeIds.sequential("inv"), auditWriter, invites,
        1_209_600L, "http://localhost:5173/studio/splits/accept");
    service = new FinalizeReleaseService(
        repo, new FakePlatformSettingsProvider(PlatformSettings.defaults()), clock,
        FakeIds.sequential("fin"), auditWriter, splitInviteService);

    repo.addArtist(new ArtistProfile(
        ARTIST, "Test Artist", null, null, false, null, null, null, null, List.of(), List.of()));
  }

  @Test
  void finalize_withPendingSplit_issuesInvite_firesEvent_andPersistsIt() {
    Release r = draftWithTrack("rel-1", "t1");
    repo.addTrack(track("t1", "Track One"));
    repo.saveTrackSplits("t1", List.of(
        new SplitEntry("sp-1", "t1", "Collaborator", "bob@x.com", "Producer", 20,
            SplitConfirmation.pending)));

    service.finalize(new ReleaseId("rel-1"), ARTIST, "key-1");

    assertEquals(1, invites.fired().size());
    SplitInviteIssued event = invites.fired().get(0);
    assertEquals("bob@x.com", event.email());
    assertFalse(event.acceptUrl() == null || event.acceptUrl().isBlank());
    assertTrue(event.acceptUrl().contains("?token="));

    String token = event.acceptUrl().substring(event.acceptUrl().indexOf("token=") + "token=".length());
    assertTrue(repo.findSplitInviteByHash(sha256Hex(token)).isPresent());
  }

  @Test
  void finalize_withNoPendingSplits_firesNoEvents() {
    draftWithTrack("rel-2", "t2");
    repo.addTrack(track("t2", "Track Two"));

    service.finalize(new ReleaseId("rel-2"), ARTIST, "key-2");

    assertTrue(invites.fired().isEmpty());
  }

  // ---- helpers ----

  private Release draftWithTrack(String releaseId, String trackId) {
    Release r = Release.createDraft(
        releaseId, ARTIST.value(), "Test Release", ReleaseType.single, Visibility.PUBLIC, null,
        null, null, NOW);
    r.addTrack(new ReleaseTrack(trackId, 0, 250L), NOW);
    repo.addRelease(r);
    return r;
  }

  private Track track(String id, String title) {
    return new Track(
        new TrackId(id), title, ARTIST, "Test Artist", null, null, 200,
        "/images/placeholder.jpg", OwnershipStatus.free, null, 0L, null, null, null, null, "ready");
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
