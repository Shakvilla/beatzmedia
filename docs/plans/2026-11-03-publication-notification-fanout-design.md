# Publication Notification Fanout Design

**Status:** Approved for implementation
**Date:** 2026-11-03
**Work unit:** WU-DIS-1, slice 2

## Outcome

Turn each durable music-release or podcast-episode publication into one notification for every
current follower without delaying publication, creating duplicate notifications, or requiring an
unbounded transaction.

## Design

The notifications module owns a durable `publication_notification_fanout` job. Transactional
publication observers resolve the same narrow catalog/studio snapshots used by discovery and insert
one job per `(publication_kind, content_id)`. Event replay is an insert no-op and never resets a
completed job.

The platform scheduler runs `notifications.publication-fanout` every five seconds. Each tick selects
a bounded set of due jobs. A transactional page processor resolves at most 100 current followers
through library's `ResolveFollowers` input port, creates notifications through the existing
`NotifyUseCase`, and advances the account-id cursor only after the whole page succeeds. The last page
marks the job complete. A page failure leaves both notifications and cursor uncommitted, records a
bounded error plus exponential retry time in a fresh transaction, and allows other jobs to continue.

Notification dedupe keys are
`publication:<publication-kind>:<content-id>:<recipient-id>`. This makes page replay, scheduler
overlap, publication-event replay, and crash recovery converge on one row per recipient. The
existing `NotificationCreated` path remains responsible for email/SMS preference checks, provider
selection, delivery attempts, and delivery retry.

## Boundaries

- Publication remains successful even when fanout creation or processing fails.
- Notifications reads catalog/studio only through their input ports and followers only through the
  library input port; it never reads another module's tables.
- The job stores a publication snapshot and follower cursor, not a copy of the follow graph.
- Unfollowing before a follower page is resolved prevents notification creation. Notifications
  already created before an unfollow remain part of the fan's historical inbox.
- Both releases and podcast episodes use the existing `release` notification wire category so the
  frontend/API contract does not change.

## Verification

Unit tests cover enqueue idempotency, page advancement, final completion, replay deduplication, and
failure without cursor advancement. Integration coverage applies the migration, processes real
follow rows into real notification rows, and proves a rerun remains duplicate-free.
