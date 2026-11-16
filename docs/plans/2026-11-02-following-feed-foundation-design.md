# Following Feed Foundation Design

**Status:** Approved for implementation  
**Date:** 2026-11-02  
**Delivery stage:** Stage 3 — Following and notification loop

## Outcome

Deliver a chronological, authenticated Following feed for music releases and podcast episodes. The
full feed lives at `/following`; Home shows a compact preview. This slice also adds the paginated
follower-resolution port that later notification fanout jobs will use.

## Boundaries

- `library` remains authoritative for artist and show follows. It exposes paged account IDs for a
  target and a read of the caller's followed target IDs; it does not create feed or notification
  rows.
- new `discovery` context owns feed projections and the `/v1/me/following` read API.
- `catalog` and `podcasts` expose narrow snapshot ports. Discovery never reads their tables.
- durable `ReleaseWentLive` and `EpisodePublished` dispatch remains the publication source.
- notification creation and outbound delivery are intentionally deferred to the next slice; feed
  projection cannot delay or roll back publication.

## Data model

`following_feed_item` stores one global, immutable snapshot per published content item:

- `kind`: `release` or `podcast-episode`;
- `content_id`, `creator_kind`, `creator_id`;
- `title`, `subtitle`, `image`, `route`;
- `published_at`, `projected_at`.

The unique `(kind, content_id)` key makes event redelivery idempotent. No fan/account identifier is
stored in this projection. At read time, discovery obtains the caller's followed artist/show IDs
through a library input port and filters the global projection. Unfollowing therefore removes items
immediately; following exposes already-projected historical items without backfilling per-fan rows.

## API

`GET /v1/me/following?cursor=&limit=` returns:

```json
{
  "items": [{
    "kind": "release",
    "contentId": "...",
    "creatorId": "...",
    "title": "...",
    "subtitle": "...",
    "image": "...",
    "route": "/album/...",
    "publishedAt": "..."
  }],
  "nextCursor": "..."
}
```

Pagination is keyset-based on `(published_at, kind, content_id)`, newest first. Limits are clamped to
1–100. The account always comes from the JWT subject.

## Frontend

The dedicated `/following` route uses an editorial timeline: date-grouped cards, distinct music and
podcast labels, artwork, creator context, and direct deep links. Home shows the newest six items in a
rail and links to the full feed. Signed-out users see neither query nor preview; the dedicated route
shows a sign-in prompt rather than sending an authenticated request.

## Failure behavior and verification

If publication content cannot be resolved, the projector fails so the durable event dispatcher can
retry; it never writes a partial snapshot. Exact replay is a no-op. Feed reads return an empty page
when the account follows nothing. Tests cover follower pagination/isolation, projection idempotency,
mixed artist/show filtering, cursor stability, auth isolation, frontend query gating, and rendering.

