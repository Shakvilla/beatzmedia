# Stage 1 Publication Parity Evidence

**Date:** 2026-11-02  
**Slice:** `ST1-03`  
**Baseline:** `5bc8027`

## Result

PASS. Release and podcast publication projections have parity through the durable outbox, and the
temporary immediate CDI delivery path has been removed from the publication application services.
The outbox dispatcher is now the sole trigger for `ReleaseWentLive` and `EpisodePublished`.

## Evidence

`PublicationOutboxParityIT` seeds a live release and a published Studio episode, appends their real
version-1 durable messages, and proves the consistency boundary:

- before dispatch: event rows are pending; album, album/track search, release pricing, podcast, and
  podcast-episode projections are absent;
- after dispatch: both events are completed; the album exists; album and track search documents are
  visible; track ownership/price is `for-sale`/700 minor units; the podcast and episode exist; and
  the show episode count is one.

The affected integration group also proves release approve/takedown/reinstate behavior, outbox
claim/replay semantics, and Studio podcast lifecycle behavior in one shared PostgreSQL test run.

## Verification

- 1,394 unit and architecture tests: PASS.
- 35 affected PostgreSQL/MinIO integration tests: PASS.
- Spotless apply/check: PASS.
- `git diff --check`: PASS.
- 67 Flyway migrations applied cleanly by the integration environment: PASS.

Expected test logs include simulated poison-event, provider, and media-transcode failures from
negative-path tests; the Maven runs completed with exit code 0.

## Operational consequence

A successful publish response now means the content transition and durable message committed
atomically. Fan-facing projections normally converge on the next five-second outbox poll. Operators
can inspect queue health and failed event metadata and can replay terminal failures through the
audited `ST1-02` controls.

The deployed Docker image must be repackaged before rebuild because the JVM Dockerfile copies
`target/quarkus-app` rather than compiling source inside the image.
