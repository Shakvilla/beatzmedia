package org.shakvilla.beatzmedia.engagement.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.engagement.application.port.in.EngagementHistory.CheckpointInput;
import org.shakvilla.beatzmedia.engagement.application.port.in.GetDiscoveryEngagementMetrics;
import org.shakvilla.beatzmedia.engagement.application.port.out.EngagementContentReader;
import org.shakvilla.beatzmedia.engagement.application.port.out.EngagementRepository;
import org.shakvilla.beatzmedia.engagement.application.service.EngagementHistoryService;
import org.shakvilla.beatzmedia.engagement.domain.CheckpointSequenceConflictException;
import org.shakvilla.beatzmedia.engagement.domain.ContentKind;
import org.shakvilla.beatzmedia.engagement.domain.EngagementEventType;
import org.shakvilla.beatzmedia.engagement.domain.ListeningCheckpoint;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

@Tag("unit")
class EngagementHistoryServiceTest {

  FakeRepository repository;
  FakeContentReader content;
  EngagementHistoryService service;

  @BeforeEach
  void setUp() {
    repository = new FakeRepository();
    content = new FakeContentReader();
    service = new EngagementHistoryService(
        repository,
        content,
        FakeIds.sequential("eng"),
        FakeClock.fixed(),
        (accountId, recommendationId, eventId, eventType, contentKind, contentId, parentId, occurredAt) -> {});
  }

  @Test
  void startsDurableSessionOnlyWhenHistoryEnabled() {
    var started = service.startSession("account-1");
    assertTrue(started.recording());
    assertEquals("eng-1", started.sessionId());
    assertEquals(1, repository.sessions.size());

    repository.enabled = false;
    var disabled = service.startSession("account-2");
    assertFalse(disabled.recording());
    assertEquals(1, repository.sessions.size());
  }

  @Test
  void exactRetryIsNoOpButConflictingReuseIsRejected() {
    String session = service.startSession("account-1").sessionId();
    CheckpointInput input = input(30);

    service.checkpoint("account-1", session, 1, input);
    service.checkpoint("account-1", session, 1, input);

    assertEquals(1, repository.checkpoints.size());
    assertEquals(1, repository.progressUpdates);
    assertThrows(
        CheckpointSequenceConflictException.class,
        () -> service.checkpoint("account-1", session, 1, input(31)));
  }

  @Test
  void staleCheckpointIsRetainedButCannotAdvanceProgress() {
    String session = service.startSession("account-1").sessionId();

    service.checkpoint("account-1", session, 2, input(40));
    service.checkpoint("account-1", session, 1, input(20));

    assertEquals(2, repository.checkpoints.size());
    assertTrue(repository.checkpoints.get(session + ":2").effective());
    assertFalse(repository.checkpoints.get(session + ":1").effective());
    assertEquals(1, repository.progressUpdates);
    assertEquals(2, repository.sessions.get(session).maxSequence());
  }

  @Test
  void disablingHistoryErasesPersonalRowsAndMakesLaterCheckpointANoOp() {
    String session = service.startSession("account-1").sessionId();
    service.checkpoint("account-1", session, 1, input(30));

    service.updatePreference("account-1", false);
    service.checkpoint("account-1", session, 2, input(45));

    assertTrue(repository.deleted);
    assertTrue(repository.checkpoints.isEmpty());
    assertFalse(service.preference("account-1").historyEnabled());
  }

  private static CheckpointInput input(int position) {
    return new CheckpointInput(
        ContentKind.TRACK,
        "track-1",
        EngagementEventType.PROGRESS,
        position,
        180,
        15,
        null);
  }

  private static final class FakeContentReader implements EngagementContentReader {
    @Override
    public ContentSnapshot get(ContentKind kind, String contentId, String accountId) {
      return new ContentSnapshot(kind, contentId, "Track", "Artist", "/cover", 180, "album-1");
    }

    @Override
    public List<ContentSnapshot> getAll(List<ContentRef> refs, String accountId) {
      return refs.stream().map(ref -> get(ref.kind(), ref.contentId(), accountId)).toList();
    }
  }

  private static final class FakeRepository implements EngagementRepository {
    boolean enabled = true;
    boolean deleted;
    int progressUpdates;
    final Map<String, SessionRow> sessions = new LinkedHashMap<>();
    final Map<String, ListeningCheckpoint> checkpoints = new LinkedHashMap<>();

    @Override
    public PreferenceRow lockPreference(String accountId, Instant now) {
      return new PreferenceRow(enabled);
    }

    @Override
    public PreferenceRow preference(String accountId) {
      return new PreferenceRow(enabled);
    }

    @Override
    public void savePreference(String accountId, boolean enabled, Instant now) {
      this.enabled = enabled;
    }

    @Override
    public void createSession(String id, String accountId, Instant startedAt) {
      sessions.put(id, new SessionRow(id, accountId, 0));
    }

    @Override
    public Optional<SessionRow> lockSession(String id, String accountId) {
      SessionRow row = sessions.get(id);
      return row != null && row.accountId().equals(accountId) ? Optional.of(row) : Optional.empty();
    }

    @Override
    public Optional<ListeningCheckpoint> checkpoint(String sessionId, long sequence) {
      return Optional.ofNullable(checkpoints.get(sessionId + ":" + sequence));
    }

    @Override
    public void insertCheckpoint(ListeningCheckpoint checkpoint) {
      checkpoints.put(checkpoint.sessionId() + ":" + checkpoint.sequence(), checkpoint);
    }

    @Override
    public void advanceSessionAndProgress(ListeningCheckpoint checkpoint) {
      sessions.put(
          checkpoint.sessionId(),
          new SessionRow(checkpoint.sessionId(), checkpoint.accountId(), checkpoint.sequence()));
      progressUpdates++;
    }

    @Override
    public List<ProgressRow> progress(
        String accountId,
        ContentKind onlyKind,
        boolean incompleteOnly,
        Cursor cursor,
        int limit) {
      return new ArrayList<>();
    }

    @Override
    public void deletePersonalHistory(String accountId, Instant clearedAt) {
      sessions.entrySet().removeIf(entry -> entry.getValue().accountId().equals(accountId));
      checkpoints.entrySet().removeIf(entry -> entry.getValue().accountId().equals(accountId));
      deleted = true;
    }

    @Override
    public int projectPending(int batchSize, Instant projectedAt) {
      return 0;
    }

    @Override
    public List<GetDiscoveryEngagementMetrics.ContentMetrics> discoveryMetrics(
        List<GetDiscoveryEngagementMetrics.ContentRef> content,
        Instant asOf) {
      return List.of();
    }

    @Override
    public List<GetDiscoveryEngagementMetrics.ContentRef> discoveryCandidates(
        Instant asOf, int perWindowLimit) {
      return List.of();
    }

    @Override
    public GetDiscoveryEngagementMetrics.GroupMetrics discoveryGroupMetrics(
        List<GetDiscoveryEngagementMetrics.ContentRef> members, Instant asOf) {
      return new GetDiscoveryEngagementMetrics.GroupMetrics(0, 0, 0, 0, 0, 0);
    }
  }
}
