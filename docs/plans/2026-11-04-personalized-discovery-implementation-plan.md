# Personalized Discovery Implementation Plan

## Backend

1. Model four recommendation kinds: single, album, EP, and podcast show.
2. Add a pure deterministic ranker with owned-release exclusion, deduplication, and four-type
   reservation rules.
3. Compose bounded Home, followed-artist release, and podcast-show candidates through existing
   catalog and podcast input ports.
4. Expose authenticated `GET /v1/me/recommendations` with a clamped 1–24 limit.
5. Add unit and API authentication tests; update the discovery architecture document.

## Web

1. Add a typed TanStack Query definition gated by authentication.
2. Add an accessible `ForYouRail` with type-specific badges, typed deep links, and visible
   recommendation reasons.
3. Replace the misleading curated-playlist “Made for you” block on Home; retain curated playlists as
   quick picks.
4. Add component/query tests, build the SPA, and run React Doctor on the diff.

## Delivery constraints

- No database migration.
- No edits to catalog persistence or engagement event recording.
- No behavioral change to Trending, Following, playback, or publication.
- Docker rebuilding and smoke execution remain with the user, per the agreed workflow.
