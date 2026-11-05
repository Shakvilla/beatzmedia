# Personalized Discovery Design

**Status:** Approved for implementation
**Date:** 2026-11-04 · **Revised:** 2026-11-05
**Delivery stage:** Discovery — explainable personalization

## Outcome

Add an authenticated **For You** rail to Home that recommends a bounded mix of singles, albums, EPs,
and podcast shows. Trending remains the public popularity view and Following remains chronological;
neither is re-ranked or replaced.

## Product behavior

`GET /v1/me/recommendations?limit=12` returns recommendation cards with a human-readable reason such
as `Because you follow Ama`, `Based on your Afrobeats listening`, or `Popular on BeatzClik`.
Recommendations exclude owned tracks, fully owned albums/EPs, and recently completed tracks. When
multiple content types are available, the result reserves space for each rather than allowing one
category to crowd out the others.

Signed-out Home does not issue the authenticated request. A cold-start account gets a balanced
popular/new fallback. Disabling listening history removes history-derived signals while leaving
library follows, likes, saves, and safe popularity fallback available.

## Architecture

Discovery owns the scoring policy and response. Integration adapters read existing input ports from
`library`, `engagement`, `catalog`, and `podcasts`; discovery never reads another module's tables.
The calculation is request-time and bounded, so this slice creates no recommendation profile or
per-account recommendation rows.

Signal reads are capped at 50 recent history items and bounded collection IDs. Candidate reads use
Home discovery, a small number of followed-artist tracks/releases, and a bounded podcast show page.
Podcast episodes are not fanned out for this rail. Candidates are deduplicated by
`(kind, contentId)` before deterministic ranking.

## Ranking

The ranker uses additive, auditable signals:

1. direct creator/show follow;
2. creator affinity from likes and saved content;
3. genre or podcast-category affinity;
4. meaningful recent listening, when enabled;
5. popularity and freshness as fallback/tie-break signals.

The wire response exposes the selected reason, not scores, raw event timestamps, follow IDs,
purchase IDs, or signal inventories.

## Failure and privacy behavior

History failure or opt-out falls back to library and popularity signals. A catalog or podcast source
failure removes only that source's candidates. Recommendation failure cannot affect `/home`,
`/following`, publication, playback, or purchases. The endpoint takes the account exclusively from
the JWT subject and accepts no account ID parameter.

## Verification

Unit tests cover weighting, owned-track/release exclusions, four-type reservation, deduplication,
cold start, opt-out, limits, and deterministic ordering. API tests cover authentication. Frontend
tests cover signed-out query gating, all card types, recommendation reasons, empty results, and
source failure behavior.
