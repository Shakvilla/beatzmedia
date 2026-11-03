# Architecture Design Doc — `discovery`

> **Status:** In progress · **Owning context:** `discovery` · **Package root:**
> `org.shakvilla.beatzmedia.discovery`

## Purpose

Discovery owns fan-facing content collections whose membership or ordering is not catalog authority.
The first delivered collection is Following: a chronological stream of published music releases and
podcast episodes from artist and show relationships owned by `library`.

## Boundary

- `catalog` and `podcasts` remain authoritative for content metadata and publication.
- `library` remains authoritative for follows and exposes narrow forward/reverse read ports.
- discovery stores global publication snapshots, never per-fan feed rows or private follow data.
- notification fanout is owned by `notifications` and is not performed by feed projection.

## Following projection

`ReleaseWentLive` and `EpisodePublished` are delivered by the durable publication outbox. An
after-success discovery observer resolves a narrow content snapshot and upserts
`following_feed_item`. `(kind, content_id)` is unique, so replay is safe. A failed content read fails
the consumer transaction and remains retryable through the durable dispatcher.

At read time, `GET /v1/me/following` obtains the JWT account's followed artist/show IDs through the
library port and filters the global projection. This makes follow and unfollow changes immediately
visible and keeps raw relationship data out of discovery persistence.

## Pagination and privacy

Items sort by `published_at DESC, id ASC`. The opaque cursor contains the timestamp, kind, and
content ID of the last item. The API accepts no account identifier and returns no follower identity.
Reverse follower pagination is exposed only as an in-process library port for future resumable
notification fanout.

