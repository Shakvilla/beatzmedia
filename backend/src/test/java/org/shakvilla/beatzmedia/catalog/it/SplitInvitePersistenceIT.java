package org.shakvilla.beatzmedia.catalog.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.InviteOutcome;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.SplitConfirmation;
import org.shakvilla.beatzmedia.catalog.domain.SplitEntry;
import org.shakvilla.beatzmedia.catalog.domain.SplitInvite;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;

import io.quarkus.test.junit.QuarkusTest;

/**
 * WU-CAT-9 (Task 2): the {@code split_invite} table + the seven new
 * {@link CatalogRepository} methods, exercised against a real Postgres via the JPA adapter.
 * Setup mirrors {@code SplitPersistenceIT} (WU-CAT-6): a draft release with one track and
 * pending collaborator splits, seeded via {@code saveTrackSplits}.
 */
@QuarkusTest
@Tag("it")
class SplitInvitePersistenceIT {

  @Inject CatalogRepository repo;

  private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");

  // Reuses the dev-seeded 'black-sherif' artist_profile row for the release.artist_id FK (same
  // pattern as SplitPersistenceIT / CatalogEnumerationIT).
  private static final String ARTIST_ID = "black-sherif";

  // split_entry.id is a global PK and these @Transactional tests commit against the shared suite
  // database, so ids must be namespaced per test class — plain 'sp-a'/'sp-b'/'sp-c' collide with
  // SplitPersistenceIT (WU-CAT-6) when the whole IT suite runs.
  private SplitEntry pendingSplit(String id, String trackId, String email, int percent) {
    return new SplitEntry(id, trackId, "Collaborator", email, "Producer",
        percent, SplitConfirmation.pending);
  }

  @Transactional
  void seedReleaseWithTrack(String releaseId, String trackId) {
    repo.saveTrack(new Track(
        new TrackId(trackId),
        trackId + " title",
        new ArtistId(ARTIST_ID),
        null,
        null,
        null,
        200,
        "/images/placeholder.jpg",
        OwnershipStatus.free,
        null,
        0L,
        null,
        null,
        null,
        null,
        "ready"));
    Release r = Release.reconstitute(
        releaseId, ARTIST_ID, "T", ReleaseType.single,
        org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus.draft,
        Visibility.PUBLIC, null, null, 0L, NOW, NOW,
        List.of(new ReleaseTrack(trackId, 0, 250L)), null, null, List.of());
    repo.saveRelease(r);
  }

  @Test
  @Transactional
  void saveSplitInvite_findByHash_roundTripsAllFields() {
    seedReleaseWithTrack("rel-inv-1", "trk-inv-1");
    Instant expiresAt = NOW.plusSeconds(86_400);
    SplitInvite invite = SplitInvite.issue(
        "inv-1", "rel-inv-1", "collab@example.com", "hash-1", expiresAt, NOW);

    repo.saveSplitInvite(invite);

    SplitInvite found = repo.findSplitInviteByHash("hash-1").orElseThrow();
    assertEquals("inv-1", found.id());
    assertEquals("rel-inv-1", found.releaseId());
    assertEquals("collab@example.com", found.email());
    assertEquals("hash-1", found.tokenHash());
    assertEquals(expiresAt, found.expiresAt());
    assertEquals(NOW, found.createdAt());
    assertNull(found.consumedAt());
    assertNull(found.outcome());
    assertFalse(found.isConsumed());
  }

  @Test
  @Transactional
  void confirmSplitsForReleaseEmail_flipsOnlyMatchingEmailToConfirmed_andSetsAccountId() {
    seedReleaseWithTrack("rel-inv-2", "trk-inv-2");
    repo.saveTrackSplits("trk-inv-2", List.of(
        pendingSplit("sp-inv-a", "trk-inv-2", "collab@example.com", 30),
        pendingSplit("sp-inv-b", "trk-inv-2", "other@example.com", 20)));

    repo.confirmSplitsForReleaseEmail(new ReleaseId("rel-inv-2"), "collab@example.com", "acct-123");

    Release read = repo.findRelease(new ReleaseId("rel-inv-2")).orElseThrow();
    SplitEntry confirmed = read.getSplits().stream()
        .filter(s -> s.email().equals("collab@example.com")).findFirst().orElseThrow();
    SplitEntry untouched = read.getSplits().stream()
        .filter(s -> s.email().equals("other@example.com")).findFirst().orElseThrow();

    assertEquals(SplitConfirmation.confirmed, confirmed.confirmation());
    assertEquals("acct-123", confirmed.accountId());
    assertEquals(SplitConfirmation.pending, untouched.confirmation());
    assertNull(untouched.accountId());

    // The other collaborator's split is still pending, so the release still has pending splits.
    assertTrue(repo.hasPendingSplits(new ReleaseId("rel-inv-2")));
  }

  @Test
  @Transactional
  void declineSplitsForReleaseEmail_flipsToDeclined_withoutSettingAccountId() {
    seedReleaseWithTrack("rel-inv-3", "trk-inv-3");
    repo.saveTrackSplits("trk-inv-3", List.of(
        pendingSplit("sp-inv-c", "trk-inv-3", "collab@example.com", 15)));

    repo.declineSplitsForReleaseEmail(new ReleaseId("rel-inv-3"), "collab@example.com");

    Release read = repo.findRelease(new ReleaseId("rel-inv-3")).orElseThrow();
    SplitEntry declined = read.getSplits().get(0);
    assertEquals(SplitConfirmation.declined, declined.confirmation());
    assertNull(declined.accountId());
    assertFalse(repo.hasPendingSplits(new ReleaseId("rel-inv-3")));
  }

  @Test
  @Transactional
  void consumeSplitInvite_setsConsumedAtAndOutcome() {
    seedReleaseWithTrack("rel-inv-4", "trk-inv-4");
    SplitInvite invite = SplitInvite.issue(
        "inv-4", "rel-inv-4", "collab@example.com", "hash-4", NOW.plusSeconds(3600), NOW);
    repo.saveSplitInvite(invite);

    Instant consumedAt = NOW.plusSeconds(60);
    repo.consumeSplitInvite("hash-4", InviteOutcome.accepted, consumedAt);

    SplitInvite refetched = repo.findSplitInviteByHash("hash-4").orElseThrow();
    assertTrue(refetched.isConsumed());
    assertEquals(InviteOutcome.accepted, refetched.outcome());
    assertEquals(consumedAt, refetched.consumedAt());
  }

  @Test
  @Transactional
  void deleteUnconsumedInvitesForReleaseEmail_removesPending_leavesConsumed() {
    seedReleaseWithTrack("rel-inv-5", "trk-inv-5");
    SplitInvite pending = SplitInvite.issue(
        "inv-5a", "rel-inv-5", "collab@example.com", "hash-5a", NOW.plusSeconds(3600), NOW);
    SplitInvite toConsume = SplitInvite.issue(
        "inv-5b", "rel-inv-5", "collab@example.com", "hash-5b", NOW.plusSeconds(3600), NOW);
    repo.saveSplitInvite(pending);
    repo.saveSplitInvite(toConsume);
    repo.consumeSplitInvite("hash-5b", InviteOutcome.declined, NOW.plusSeconds(10));

    repo.deleteUnconsumedInvitesForReleaseEmail(new ReleaseId("rel-inv-5"), "collab@example.com");

    assertTrue(repo.findSplitInviteByHash("hash-5a").isEmpty());
    assertTrue(repo.findSplitInviteByHash("hash-5b").isPresent());
  }

  @Test
  @Transactional
  void pendingSplitEmailsForRelease_returnsDistinctPendingEmailsOnly() {
    seedReleaseWithTrack("rel-inv-6", "trk-inv-6");
    repo.saveTrackSplits("trk-inv-6", List.of(
        pendingSplit("sp-inv-d", "trk-inv-6", "a@example.com", 10),
        pendingSplit("sp-inv-e", "trk-inv-6", "a@example.com", 5),
        pendingSplit("sp-inv-f", "trk-inv-6", "b@example.com", 20)));
    repo.confirmSplitsForReleaseEmail(new ReleaseId("rel-inv-6"), "b@example.com", "acct-b");

    List<String> emails = repo.pendingSplitEmailsForRelease(new ReleaseId("rel-inv-6"));

    assertEquals(1, emails.size());
    assertEquals("a@example.com", emails.get(0));
  }
}
