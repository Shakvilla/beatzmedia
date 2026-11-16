package org.shakvilla.beatzmedia.notifications.application.service;

import java.time.Duration;
import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.ResolveFollowers;
import org.shakvilla.beatzmedia.library.domain.FollowKind;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyCommand;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyUseCase;
import org.shakvilla.beatzmedia.notifications.application.port.out.PublicationFanoutRepository;
import org.shakvilla.beatzmedia.notifications.application.port.out.PublicationNotificationContentReader;
import org.shakvilla.beatzmedia.notifications.domain.FanoutStatus;
import org.shakvilla.beatzmedia.notifications.domain.NotificationType;
import org.shakvilla.beatzmedia.notifications.domain.PublicationFanoutJob;
import org.shakvilla.beatzmedia.notifications.domain.PublicationKind;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/** Enqueues and advances bounded, retryable follower notification fanout pages. */
@ApplicationScoped
public class PublicationFanoutService {

  static final int FOLLOWER_PAGE_SIZE = 100;
  private static final int MAX_ERROR_LENGTH = 1000;

  private final PublicationFanoutRepository repository;
  private final PublicationNotificationContentReader content;
  private final ResolveFollowers followers;
  private final NotifyUseCase notifications;
  private final Clock clock;
  private final IdGenerator ids;

  @Inject
  public PublicationFanoutService(
      PublicationFanoutRepository repository,
      PublicationNotificationContentReader content,
      ResolveFollowers followers,
      NotifyUseCase notifications,
      Clock clock,
      IdGenerator ids) {
    this.repository = repository;
    this.content = content;
    this.followers = followers;
    this.notifications = notifications;
    this.clock = clock;
    this.ids = ids;
  }

  @Transactional
  public void enqueueRelease(String releaseId) {
    var snapshot = content.release(releaseId);
    enqueue(
        PublicationKind.release,
        snapshot.contentId(),
        "artist",
        snapshot.creatorId(),
        "New release from " + snapshot.creatorName(),
        snapshot.title() + " is out now.",
        snapshot.route());
  }

  @Transactional
  public void enqueueEpisode(String artistId, String episodeId) {
    var snapshot = content.episode(artistId, episodeId);
    enqueue(
        PublicationKind.podcast_episode,
        snapshot.contentId(),
        "show",
        snapshot.creatorId(),
        "New episode from " + snapshot.creatorName(),
        snapshot.title() + " is available now.",
        snapshot.route());
  }

  /**
   * Creates a whole notification page and advances its cursor in one transaction. If any recipient
   * fails, neither the page's notifications nor its cursor commit; replay is deduped by NotifyUseCase.
   */
  @Transactional
  public void processPage(PublicationFanoutJob job) {
    FollowKind kind = "artist".equals(job.targetKind()) ? FollowKind.artist : FollowKind.show;
    var page = followers.resolve(kind, job.targetId(), job.cursor(), FOLLOWER_PAGE_SIZE);
    for (String accountId : page.accountIds()) {
      notifications.notify(
          new NotifyCommand(
              dedupeKey(job, accountId),
              new AccountId(accountId),
              NotificationType.release,
              job.title(),
              job.body(),
              job.route()));
    }

    Instant now = clock.now();
    if (page.nextCursor() == null) {
      repository.complete(job.id(), now);
    } else {
      repository.advance(job.id(), page.nextCursor(), now);
    }
  }

  /** Records retry state outside the failed page transaction. */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void recordFailure(PublicationFanoutJob job, RuntimeException failure) {
    int retryCount = job.retryCount() + 1;
    Instant now = clock.now();
    repository.reschedule(
        job.id(),
        retryCount,
        now.plus(retryDelay(retryCount)),
        boundedError(failure),
        now);
  }

  static Duration retryDelay(int retryCount) {
    int exponent = Math.max(0, Math.min(retryCount - 1, 5));
    return Duration.ofSeconds(Math.min(900L, 30L * (1L << exponent)));
  }

  private void enqueue(
      PublicationKind kind,
      String contentId,
      String targetKind,
      String targetId,
      String title,
      String body,
      String route) {
    Instant now = clock.now();
    repository.enqueue(
        new PublicationFanoutJob(
            ids.newId(),
            kind,
            contentId,
            targetKind,
            targetId,
            title,
            body,
            route,
            null,
            FanoutStatus.pending,
            0,
            now,
            null,
            now,
            now,
            null));
  }

  private static String dedupeKey(PublicationFanoutJob job, String accountId) {
    return "publication:"
        + job.publicationKind().name()
        + ":"
        + job.contentId()
        + ":"
        + accountId;
  }

  private static String boundedError(RuntimeException failure) {
    String message = failure.getMessage();
    String value =
        failure.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
    return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
  }
}
