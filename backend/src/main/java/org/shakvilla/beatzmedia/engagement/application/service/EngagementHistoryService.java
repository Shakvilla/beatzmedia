package org.shakvilla.beatzmedia.engagement.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.discovery.application.port.in.RecordRecommendationOutcome;
import org.shakvilla.beatzmedia.engagement.application.port.in.EngagementHistory;
import org.shakvilla.beatzmedia.engagement.application.port.out.EngagementContentReader;
import org.shakvilla.beatzmedia.engagement.application.port.out.EngagementContentReader.ContentRef;
import org.shakvilla.beatzmedia.engagement.application.port.out.EngagementContentReader.ContentSnapshot;
import org.shakvilla.beatzmedia.engagement.application.port.out.EngagementRepository;
import org.shakvilla.beatzmedia.engagement.application.port.out.EngagementRepository.Cursor;
import org.shakvilla.beatzmedia.engagement.application.port.out.EngagementRepository.ProgressRow;
import org.shakvilla.beatzmedia.engagement.domain.CheckpointSequenceConflictException;
import org.shakvilla.beatzmedia.engagement.domain.ContentKind;
import org.shakvilla.beatzmedia.engagement.domain.ListeningCheckpoint;
import org.shakvilla.beatzmedia.engagement.domain.ListeningSessionNotFoundException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

@ApplicationScoped
public class EngagementHistoryService implements EngagementHistory {

  private static final org.jboss.logging.Logger LOG =
      org.jboss.logging.Logger.getLogger(EngagementHistoryService.class);

  private final EngagementRepository repository;
  private final EngagementContentReader contentReader;
  private final IdGenerator ids;
  private final Clock clock;
  private final RecordRecommendationOutcome recommendationOutcomes;

  @Inject
  public EngagementHistoryService(
      EngagementRepository repository,
      EngagementContentReader contentReader,
      IdGenerator ids,
      Clock clock,
      RecordRecommendationOutcome recommendationOutcomes) {
    this.repository = repository;
    this.contentReader = contentReader;
    this.ids = ids;
    this.clock = clock;
    this.recommendationOutcomes = recommendationOutcomes;
  }

  @Override
  @Transactional
  public SessionStart startSession(String accountId) {
    Instant now = clock.now();
    if (!repository.lockPreference(accountId, now).historyEnabled()) {
      return new SessionStart(false, null, null);
    }
    String sessionId = ids.newId();
    repository.createSession(sessionId, accountId, now);
    return new SessionStart(true, sessionId, now);
  }

  @Override
  @Transactional
  public void checkpoint(
      String accountId, String sessionId, long sequence, CheckpointInput input) {
    if (input == null) throw new ValidationException("checkpoint body is required");
    Instant now = clock.now();
    if (!repository.lockPreference(accountId, now).historyEnabled()) return;

    EngagementRepository.SessionRow session = repository
        .lockSession(sessionId, accountId)
        .orElseThrow(ListeningSessionNotFoundException::new);

    var existing = repository.checkpoint(sessionId, sequence);
    if (existing.isPresent()) {
      if (!sameRequest(existing.get(), input)) {
        throw new CheckpointSequenceConflictException(sequence);
      }
      return;
    }

    ContentSnapshot content = contentReader.get(input.contentKind(), input.contentId(), accountId);
    if (Math.abs(content.durationSeconds() - input.durationSeconds()) > 2) {
      throw new ValidationException("duration does not match content", "durationSeconds");
    }

    ListeningCheckpoint candidate = new ListeningCheckpoint(
        ids.newId(),
        sessionId,
        accountId,
        sequence,
        input.contentKind(),
        input.contentId(),
        input.eventType(),
        input.positionSeconds(),
        content.durationSeconds(),
        input.listenedDeltaSeconds(),
        sequence > session.maxSequence(),
        now);

    repository.insertCheckpoint(candidate);
    if (candidate.effective()) {
      repository.advanceSessionAndProgress(candidate);
      recordRecommendationOutcome(candidate, input.recommendationId(), content.parentId());
    }
  }

  @Override
  @Transactional(Transactional.TxType.SUPPORTS)
  public HistoryPage recentlyPlayed(String accountId, String cursor, int limit) {
    return history(accountId, null, false, cursor, limit);
  }

  @Override
  @Transactional(Transactional.TxType.SUPPORTS)
  public HistoryPage continueListening(String accountId, String cursor, int limit) {
    return history(accountId, ContentKind.PODCAST_EPISODE, true, cursor, limit);
  }

  @Override
  @Transactional(Transactional.TxType.SUPPORTS)
  public Preference preference(String accountId) {
    return new Preference(repository.preference(accountId).historyEnabled());
  }

  @Override
  @Transactional
  public Preference updatePreference(String accountId, boolean enabled) {
    Instant now = clock.now();
    repository.lockPreference(accountId, now);
    repository.savePreference(accountId, enabled, now);
    if (!enabled) repository.deletePersonalHistory(accountId, now);
    return new Preference(enabled);
  }

  @Override
  @Transactional
  public void clearHistory(String accountId) {
    Instant now = clock.now();
    repository.lockPreference(accountId, now);
    repository.deletePersonalHistory(accountId, now);
  }

  private HistoryPage history(
      String accountId, ContentKind kind, boolean incomplete, String rawCursor, int requestedLimit) {
    int limit = requestedLimit == 0 ? 20 : requestedLimit;
    if (limit < 1 || limit > 100) throw new ValidationException("limit must be 1..100", "limit");
    List<ProgressRow> rows =
        repository.progress(accountId, kind, incomplete, decode(rawCursor), limit + 1);
    boolean hasMore = rows.size() > limit;
    List<ProgressRow> pageRows = hasMore ? rows.subList(0, limit) : rows;

    List<ContentRef> refs = pageRows.stream()
        .map(row -> new ContentRef(row.contentKind(), row.contentId()))
        .toList();
    Map<String, ContentSnapshot> snapshots = new LinkedHashMap<>();
    for (ContentSnapshot snapshot : contentReader.getAll(refs, accountId)) {
      snapshots.put(key(snapshot.kind(), snapshot.contentId()), snapshot);
    }

    List<HistoryItem> items = new ArrayList<>();
    for (ProgressRow row : pageRows) {
      ContentSnapshot snapshot = snapshots.get(key(row.contentKind(), row.contentId()));
      if (snapshot == null) continue;
      items.add(new HistoryItem(
          row.contentKind().wireValue(),
          row.contentId(),
          snapshot.title(),
          snapshot.subtitle(),
          snapshot.image(),
          row.positionSeconds(),
          row.durationSeconds(),
          row.completed(),
          row.lastActivityAt()));
    }
    String next = hasMore ? encode(pageRows.getLast()) : null;
    return new HistoryPage(List.copyOf(items), next);
  }

  private static String key(ContentKind kind, String id) {
    return kind.wireValue() + "\n" + id;
  }

  private static boolean sameRequest(ListeningCheckpoint existing, CheckpointInput input) {
    return existing.contentKind() == input.contentKind()
        && existing.contentId().equals(input.contentId())
        && existing.eventType() == input.eventType()
        && existing.positionSeconds() == input.positionSeconds()
        && Math.abs(existing.durationSeconds() - input.durationSeconds()) <= 2
        && existing.listenedDeltaSeconds() == input.listenedDeltaSeconds();
  }

  private void recordRecommendationOutcome(
      ListeningCheckpoint checkpoint, String recommendationId, String parentId) {
    try {
      recommendationOutcomes.record(
          checkpoint.accountId(),
          recommendationId,
          checkpoint.id(),
          checkpoint.eventType().wireValue(),
          checkpoint.contentKind().wireValue(),
          checkpoint.contentId(),
          parentId,
          checkpoint.receivedAt());
    } catch (RuntimeException failure) {
      LOG.warn("Recommendation attribution failed without affecting listening", failure);
    }
  }

  private static String encode(ProgressRow row) {
    String value = row.lastActivityAt().toEpochMilli()
        + "|" + row.contentKind().wireValue() + "|" + row.contentId();
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static Cursor decode(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      String value = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
      String[] parts = value.split("\\|", 3);
      return new Cursor(
          Instant.ofEpochMilli(Long.parseLong(parts[0])),
          ContentKind.fromWire(parts[1]),
          parts[2]);
    } catch (RuntimeException e) {
      throw new ValidationException("Invalid history cursor", "cursor");
    }
  }
}
