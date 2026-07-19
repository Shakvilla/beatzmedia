package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.shakvilla.beatzmedia.catalog.application.port.in.SplitInviteView;
import org.shakvilla.beatzmedia.catalog.application.service.SplitInviteService;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.InviteOutcome;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.SplitConfirmation;
import org.shakvilla.beatzmedia.catalog.domain.SplitEntry;
import org.shakvilla.beatzmedia.catalog.domain.SplitInvite;
import org.shakvilla.beatzmedia.catalog.domain.SplitInviteGoneException;
import org.shakvilla.beatzmedia.catalog.domain.SplitInviteIssued;
import org.shakvilla.beatzmedia.catalog.domain.SplitInviteNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.catalog.fakes.RecordingEvent;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link SplitInviteService}. Covers WU-CAT-9 accept/decline/get-by-token: only the
 * addressed collaborator's rows are touched, expired/consumed tokens are rejected (410), unknown
 * tokens 404, and every mutation appends an AuditEntry (INV-10).
 */
@Tag("unit")
class SplitInviteServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");
  private static final String ARTIST = "artist-1";
  private static final String PLAIN_TOKEN = "plain-token";

  private FakeCatalogRepository repo;
  private FakeAuditWriter auditWriter;
  private FakeClock clock;
  private RecordingEvent<SplitInviteIssued> invites;
  private SplitInviteService service;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    auditWriter = new FakeAuditWriter();
    clock = FakeClock.at(NOW);
    invites = new RecordingEvent<>();
    service = new SplitInviteService(
        repo, clock, FakeIds.sequential("aud"), auditWriter, invites,
        1_209_600L, "http://localhost:5173/studio/splits/accept");

    repo.addArtist(new ArtistProfile(
        new ArtistId(ARTIST), "Test Artist", null, null, false, null, null, null, null,
        List.of(), List.of()));
  }

  // ---- accept ----

  @Test
  void accept_confirmsAllReleaseRowsForEmail_linksAccount_consumesInvite_andAudits() {
    seedReleaseWithSplits("rel-1", "t1", "t2");
    seedInvite("rel-1", "bob@x.com", NOW.plusSeconds(3600));

    service.accept(PLAIN_TOKEN, "acc-bob");

    Release release = repo.findRelease(new ReleaseId("rel-1")).orElseThrow();
    List<SplitEntry> bobEntries = release.getSplits().stream()
        .filter(s -> s.email().equals("bob@x.com")).toList();
    assertEquals(2, bobEntries.size());
    for (SplitEntry s : bobEntries) {
      assertEquals(SplitConfirmation.confirmed, s.confirmation());
      assertEquals("acc-bob", s.accountId());
    }
    SplitEntry aliceEntry = release.getSplits().stream()
        .filter(s -> s.email().equals("alice@x.com")).findFirst().orElseThrow();
    assertEquals(SplitConfirmation.pending, aliceEntry.confirmation());
    assertNull(aliceEntry.accountId());

    SplitInvite invite = repo.findSplitInviteByHash(sha256Hex(PLAIN_TOKEN)).orElseThrow();
    assertTrue(invite.isConsumed());
    assertEquals(InviteOutcome.accepted, invite.outcome());

    assertEquals(1, auditWriter.size());
    assertEquals("ACCEPT_SPLIT_INVITE", auditWriter.all().get(0).getAction());
    assertEquals("acc-bob", auditWriter.all().get(0).getActor());
  }

  @Test
  void accept_unknownToken_throwsSplitInviteNotFound() {
    assertThrows(SplitInviteNotFoundException.class, () -> service.accept("nope", "acc-x"));
    assertEquals(0, auditWriter.size());
  }

  @Test
  void accept_expiredToken_throwsSplitInviteGone() {
    seedReleaseWithSplits("rel-2", "t3", "t4");
    seedInvite("rel-2", "bob@x.com", NOW.plusSeconds(10));

    clock.advanceSeconds(20);

    assertThrows(SplitInviteGoneException.class, () -> service.accept(PLAIN_TOKEN, "acc-bob"));
    assertEquals(0, auditWriter.size());
  }

  @Test
  void accept_consumedToken_throwsSplitInviteGone() {
    seedReleaseWithSplits("rel-3", "t5", "t6");
    seedInvite("rel-3", "bob@x.com", NOW.plusSeconds(3600));
    repo.consumeSplitInvite(sha256Hex(PLAIN_TOKEN), InviteOutcome.accepted, NOW);

    assertThrows(SplitInviteGoneException.class, () -> service.accept(PLAIN_TOKEN, "acc-bob"));
    assertEquals(0, auditWriter.size());
  }

  // ---- decline ----

  @Test
  void decline_setsDeclined_noAccount_consumesInvite_andAudits() {
    seedReleaseWithSplits("rel-4", "t7", "t8");
    seedInvite("rel-4", "bob@x.com", NOW.plusSeconds(3600));

    service.decline(PLAIN_TOKEN);

    Release release = repo.findRelease(new ReleaseId("rel-4")).orElseThrow();
    List<SplitEntry> bobEntries = release.getSplits().stream()
        .filter(s -> s.email().equals("bob@x.com")).toList();
    assertEquals(2, bobEntries.size());
    for (SplitEntry s : bobEntries) {
      assertEquals(SplitConfirmation.declined, s.confirmation());
      assertNull(s.accountId());
    }

    SplitInvite invite = repo.findSplitInviteByHash(sha256Hex(PLAIN_TOKEN)).orElseThrow();
    assertTrue(invite.isConsumed());
    assertEquals(InviteOutcome.declined, invite.outcome());

    assertEquals(1, auditWriter.size());
    assertEquals("DECLINE_SPLIT_INVITE", auditWriter.all().get(0).getAction());
  }

  // ---- getByToken ----

  @Test
  void getByToken_returnsView_withStatusPending() {
    seedReleaseWithSplits("rel-5", "t9", "t10");
    seedInvite("rel-5", "bob@x.com", NOW.plusSeconds(3600));

    SplitInviteView view = service.getByToken(PLAIN_TOKEN);

    assertEquals("pending", view.status());
    assertEquals("Test Artist", view.artistName());
    assertEquals("Test Release", view.releaseTitle());
    assertEquals(2, view.tracks().size());
    assertTrue(view.tracks().stream().allMatch(t -> t.role().equals("Producer")));
  }

  @Test
  void getByToken_returnsView_withStatusAccepted() {
    seedReleaseWithSplits("rel-6", "t11", "t12");
    seedInvite("rel-6", "bob@x.com", NOW.plusSeconds(3600));
    service.accept(PLAIN_TOKEN, "acc-bob");

    SplitInviteView view = service.getByToken(PLAIN_TOKEN);

    assertEquals("accepted", view.status());
  }

  @Test
  void getByToken_returnsView_withStatusExpired() {
    seedReleaseWithSplits("rel-7", "t13", "t14");
    seedInvite("rel-7", "bob@x.com", NOW.plusSeconds(10));
    clock.advanceSeconds(20);

    SplitInviteView view = service.getByToken(PLAIN_TOKEN);

    assertEquals("expired", view.status());
  }

  // ---- helpers ----

  private void seedReleaseWithSplits(String releaseId, String track1, String track2) {
    Release r = Release.createDraft(
        releaseId, ARTIST, "Test Release", ReleaseType.ep, Visibility.PUBLIC, null, null, null, NOW);
    r.addTrack(new ReleaseTrack(track1, 0, 250L), NOW);
    r.addTrack(new ReleaseTrack(track2, 1, 250L), NOW);
    repo.addRelease(r);
    repo.addTrack(track(track1, "Track One"));
    repo.addTrack(track(track2, "Track Two"));
    repo.saveTrackSplits(track1, List.of(
        pendingSplit("sp-1", track1, "bob@x.com", 20),
        pendingSplit("sp-2", track1, "alice@x.com", 10)));
    repo.saveTrackSplits(track2, List.of(
        pendingSplit("sp-3", track2, "bob@x.com", 15)));
  }

  private void seedInvite(String releaseId, String email, Instant expiresAt) {
    SplitInvite invite = SplitInvite.issue(
        "inv-seed", releaseId, email, sha256Hex(PLAIN_TOKEN), expiresAt, NOW);
    repo.saveSplitInvite(invite);
  }

  private SplitEntry pendingSplit(String id, String trackId, String email, int percent) {
    return new SplitEntry(id, trackId, "Collaborator", email, "Producer", percent,
        SplitConfirmation.pending);
  }

  private Track track(String id, String title) {
    return new Track(
        new TrackId(id), title, new ArtistId(ARTIST), "Test Artist", null, null, 200,
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
