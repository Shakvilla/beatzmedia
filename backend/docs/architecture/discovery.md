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

## Explainable personalized recommendations

`GET /v1/me/recommendations` composes bounded reads through the `library`, `engagement`, `catalog`,
and `podcasts` input ports. Discovery owns the ranking policy but does not copy private activity into
a profile table or persist per-account recommendation rows.

The deterministic ranker prioritizes direct follows, creator affinity, genre/category affinity,
meaningful recent listening, freshness, and popularity. Owned singles, fully owned albums/EPs, and
recently completed tracks are excluded. Candidates are deduplicated, and singles, albums, EPs, and
podcast shows each receive space when available. Each item returns one plain-language reason; raw
scores and source signal inventories remain internal.

History opt-out or history-read failure removes history-derived signals. Catalog and podcast
candidate failures degrade independently. This request-time path cannot affect public Home,
Following, publication, playback, or commerce.

