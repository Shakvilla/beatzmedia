package org.shakvilla.beatzmedia.notifications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.library.application.port.in.ResolveFollowers;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyCommand;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyUseCase;
import org.shakvilla.beatzmedia.notifications.application.port.out.PublicationFanoutRepository;
import org.shakvilla.beatzmedia.notifications.application.port.out.PublicationNotificationContentReader;
import org.shakvilla.beatzmedia.notifications.application.service.PublicationFanoutService;
import org.shakvilla.beatzmedia.notifications.domain.FanoutStatus;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.notifications.domain.PublicationFanoutJob;
import org.shakvilla.beatzmedia.notifications.domain.PublicationKind;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

@Tag("unit")
class PublicationFanoutServiceTest {

  FakeFanoutRepository repository;
  RecordingNotifications notifications;
  FakeClock clock;
  ResolveFollowers followers;
  PublicationFanoutService service;

  @BeforeEach
  void setUp() {
    repository = new FakeFanoutRepository();
    notifications = new RecordingNotifications();
    clock = FakeClock.at("2026-11-03T10:00:00Z");
    followers =
        (kind, targetId, cursor, limit) ->
            cursor == null
                ? new ResolveFollowers.FollowerPage(List.of("acct-a", "acct-b"), "acct-b")
                : new ResolveFollowers.FollowerPage(List.of("acct-c"), null);
    service =
        new PublicationFanoutService(
            repository,
            new FakeContentReader(),
            followers,
            notifications,
            clock,
            FakeIds.sequential("fanout"));
  }

  @Test
  void publicationReplayEnqueuesOneDurableJob() {
    service.enqueueRelease("release-1");
    service.enqueueRelease("release-1");

    assertEquals(1, repository.jobs.size());
    PublicationFanoutJob job = repository.jobs.values().iterator().next();
    assertEquals(PublicationKind.release, job.publicationKind());
    assertEquals("artist-1", job.targetId());
    assertEquals("New release from Ama", job.title());
  }

  @Test
  void advancesOneBoundedPageThenCompletesTheLastPage() {
    PublicationFanoutJob job = pendingJob();
    repository.enqueue(job);

    service.processPage(job);

    PublicationFanoutJob advanced = repository.jobs.get(job.id());
    assertEquals("acct-b", advanced.cursor());
    assertEquals(FanoutStatus.pending, advanced.status());
    assertEquals(2, notifications.commands.size());

    service.processPage(advanced);

    assertEquals(FanoutStatus.completed, repository.jobs.get(job.id()).status());
    assertEquals(3, notifications.commands.size());
    assertEquals(
        "publication:release:release-1:acct-c",
        notifications.commands.getLast().dedupeKey());
  }

  @Test
  void failedPageDoesNotAdvanceAndIsRescheduled() {
    PublicationFanoutJob job = pendingJob();
    repository.enqueue(job);
    notifications.failRecipient = "acct-b";

    RuntimeException failure = assertThrows(RuntimeException.class, () -> service.processPage(job));
    assertNull(repository.jobs.get(job.id()).cursor());

    service.recordFailure(job, failure);

    PublicationFanoutJob retried = repository.jobs.get(job.id());
    assertEquals(1, retried.retryCount());
    assertEquals(Instant.parse("2026-11-03T10:00:30Z"), retried.nextAttemptAt());
    assertEquals("RuntimeException: simulated recipient failure", retried.lastError());
  }

  private PublicationFanoutJob pendingJob() {
    Instant now = clock.now();
    return new PublicationFanoutJob(
        "job-1",
        PublicationKind.release,
        "release-1",
        "artist",
        "artist-1",
        "New release from Ama",
        "First Light is out now.",
        "/album/release-1",
        null,
        FanoutStatus.pending,
        0,
        now,
        null,
        now,
        now,
        null);
  }

  static class FakeContentReader implements PublicationNotificationContentReader {
    @Override
    public ContentSnapshot release(String releaseId) {
      return new ContentSnapshot(releaseId, "artist-1", "Ama", "First Light", "/album/" + releaseId);
    }

    @Override
    public ContentSnapshot episode(String artistId, String episodeId) {
      return new ContentSnapshot(episodeId, "show-1", "Accra Talks", "Episode One", "/podcast/show-1");
    }
  }

  static class RecordingNotifications implements NotifyUseCase {
    final List<NotifyCommand> commands = new ArrayList<>();
    String failRecipient;

    @Override
    public NotificationId notify(NotifyCommand command) {
      if (command.recipient().value().equals(failRecipient)) {
        throw new RuntimeException("simulated recipient failure");
      }
      if (commands.stream().noneMatch(existing -> existing.dedupeKey().equals(command.dedupeKey()))) {
        commands.add(command);
      }
      return new NotificationId("notification-" + command.recipient().value());
    }
  }

  static class FakeFanoutRepository implements PublicationFanoutRepository {
    final Map<String, PublicationFanoutJob> jobs = new LinkedHashMap<>();

    @Override
    public void enqueue(PublicationFanoutJob job) {
      boolean alreadyExists =
          jobs.values().stream()
              .anyMatch(
                  existing ->
                      existing.publicationKind() == job.publicationKind()
                          && existing.contentId().equals(job.contentId()));
      if (!alreadyExists) {
        jobs.put(job.id(), job);
      }
    }

    @Override
    public List<PublicationFanoutJob> findDue(Instant now, int limit) {
      return jobs.values().stream()
          .filter(job -> job.status() == FanoutStatus.pending && !job.nextAttemptAt().isAfter(now))
          .limit(limit)
          .toList();
    }

    @Override
    public void advance(String jobId, String nextCursor, Instant now) {
      PublicationFanoutJob job = jobs.get(jobId);
      jobs.put(jobId, copy(job, nextCursor, FanoutStatus.pending, 0, now, null, now, null));
    }

    @Override
    public void complete(String jobId, Instant now) {
      PublicationFanoutJob job = jobs.get(jobId);
      jobs.put(jobId, copy(job, job.cursor(), FanoutStatus.completed, 0, now, null, now, now));
    }

    @Override
    public void reschedule(
        String jobId, int retryCount, Instant nextAttemptAt, String error, Instant now) {
      PublicationFanoutJob job = jobs.get(jobId);
      jobs.put(
          jobId,
          copy(job, job.cursor(), FanoutStatus.pending, retryCount, nextAttemptAt, error, now, null));
    }

    private static PublicationFanoutJob copy(
        PublicationFanoutJob job,
        String cursor,
        FanoutStatus status,
        int retryCount,
        Instant nextAttemptAt,
        String error,
        Instant updatedAt,
        Instant completedAt) {
      return new PublicationFanoutJob(
          job.id(),
          job.publicationKind(),
          job.contentId(),
          job.targetKind(),
          job.targetId(),
          job.title(),
          job.body(),
          job.route(),
          cursor,
          status,
          retryCount,
          nextAttemptAt,
          error,
          job.createdAt(),
          updatedAt,
          completedAt);
    }
  }
}
