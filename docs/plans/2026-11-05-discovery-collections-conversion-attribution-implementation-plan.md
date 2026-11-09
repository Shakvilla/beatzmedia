# WU-DIS-4 implementation plan

**Status:** In progress. The versioned organic chart scoring policy, bounded engagement metrics
read, candidate-window selection, published release/show grouping, grouped listener metrics, and
atomic collection snapshot repository are implemented. Library save/follow and commerce settled-purchase
read ports are implemented. A snapshot assembler now combines grouped listening, saves,
show follows, and settled purchases for Top and Trending. Catalog releases and studio podcast shows
now carry optional creator-declared origin countries, which the assembler filters by exact match.
It does not publish snapshots. Public collections,
placements, and card-level conversion attribution are pending.
**Design:** [Discovery collections and conversion attribution](2026-11-05-discovery-collections-conversion-attribution-design.md)

## 1. Content-level metric projection

- Completed: engagement exposes a bounded content-level read of qualified plays, distinct qualified
  listeners, and completions for the 28-day, 24-hour, and preceding seven-day windows. It caps
  repeats per account and UTC day and has a supporting partial index. PostgreSQL integration tests
  verify the SQL and clean-schema migration through V1002.
- Before broad candidate scans, materialize the engagement metric window or page candidate IDs so
  snapshot generation never reads an unbounded set of raw checkpoints. Do not infer unique listeners
  from starts or artist-level analytics.
- Completed: library exposes recent track likes, album/EP saves, and artist/show follows via a
  bounded aggregate input port, with indexes in V1004. Follows are creator/show growth, not
  card-attributed conversions; do not multiply one creator's follows across every release when
  ranking. The PostgreSQL integration test passed against a clean schema, including the exclusive
  as-of upper bound.
- Completed: commerce exposes distinct settled, non-refunded order counts for single tracks,
  albums/EPs (including member-track purchases), and podcast shows (including episode and season
  pass purchases). It reads the exactly-once order grant posting and filters refunded orders.
  Indexes are in V1005. This is an organic purchase signal, not evidence that a particular
  collection card caused the purchase. The PostgreSQL integration test passed against a clean
  schema through V1005.
- Add confirmed creator-support counts where a show/content association exists. Keep money
  authority in commerce/payments; do not infer show-level tips from creator-only events.
- Completed: release and show origin countries are nullable, explicitly supplied ISO codes stored
  in owning modules (V1006) and carried through discovery read ports. Existing content stays
  unassigned; the assembler excludes unknown or mismatched countries. Creator declarations are not
  independently verified, so moderation remains necessary before public country chart activation.
  A clean PostgreSQL run applied all 88 migrations through V1006, and the persistence/read-port
  integration test passed.
- Resolve album/EP track membership and podcast episode/show membership through catalog/podcast input ports. Roll child activity into the parent once, retaining direct singles and episode identities.
- Validate deduplication, daily cutoff in UTC, replay caps, self-activity exclusion, publication eligibility, and null/missing metrics. Test small and high-volume windows.

## 2. Snapshot generation and persistence

- Completed: V1003 adds immutable version and entry tables, with one active version per market and
  slug. The repository validates complete ordered entries, serializes publication per collection,
  rejects stale runs, and activates a new version transactionally. Fresh-schema PostgreSQL testing
  verifies activation and retained history.
- Decide whether content-level metrics need materialization before enabling broad chart scans; the
  current bounded read supports up to 100 specified content IDs per call.
- Candidate selection must start from bounded recent engagement windows (28-day sustained and
  24-hour acceleration) and resolve publication through catalog/podcast ports. The existing Home
  top-ten and newest rails are too narrow to be authoritative chart candidate sources. A candidate
  source should union both windows before scoring and retain grouped release/show identities.
  The bounded union of sustained and recent engagement windows is implemented. The content adapter
  resolves live releases and published episodes into singles, albums, EPs, and podcast shows with
  full track/episode membership. The engagement port now deduplicates listeners across those
  members; the remaining creator-support association, signal calibration, and snapshot job are next.
- Add operational rollback and bounded history cleanup. Snapshot publication and active-version
  reads are already implemented; keep the history needed for version-pinned cursors.
- Build `Top`, `Trending`, and `Emerging` using `CollectionRanker`; apply genre, Ghana, music, and podcast eligibility before ranking. Treat formula/threshold changes as a new version. Calibrate initial thresholds against representative fixtures before enabling public charts.
- Completed: the Top/Trending assembler creates ranked, grouped snapshot entries using bounded
  signals. It excludes future publication dates, never spreads artist-level follows to every
  release, and retains the previous snapshot when no card passes the threshold. Emerging remains
  disabled in the assembler because lifetime and creator reach are not yet available; treating
  those missing values as zero would falsely qualify established creators. Country association
  is now explicit; moderation and a publication-state recheck are required before scheduling.
- Schedule a bounded job through the platform job registry. On failure, retain the previous active version and report job age/failure. Test transaction rollback and repeat runs against PostgreSQL.

## 3. Public API and web

- Expose collection summaries and version-pinned cursor pages under `/v1/discovery/collections`. Validate market, media, slug, cursor, and page bounds. Return grouped single/album/EP/show/episode cards with a reason and placement type.
- Adapt existing `/v1/home` rails to the active collections while retaining the current response contract; gate activation so an empty or unhealthy snapshot does not degrade Home. Add a new web collection surface and accessible grouped cards.
- Add contract tests for response shapes and versioned pagination; frontend tests for every content kind, labels, navigation, empty states, and responsive layout.

## 4. Editorial and sponsored positions

- Extend editorial mutations with a validated content reference, market, collection, position, and schedule. Preserve RBAC and append an audit entry. Resolve publication state on read.
- Merge scheduled editorial/sponsored cards into reserved positions after organic reads, deduplicate, and retain organic share. Always label sponsorship in API and UI. Test expired/unpublished placements and placement collisions.

## 5. Attribution and conversion

- Generalize WU-DIS-3's short-lived attribution and event ingestion to collection cards while preserving account isolation and 90-day raw-event cleanup. Record collection version, market, position, and placement type.
- Carry optional attribution from a matching card into cart and immutable order lines. Record purchase only from confirmed settlement through an idempotent observer. Add the same path to confirmed creator support. Represent refunds separately for net reporting.
- Produce privacy-safe collection/version aggregates and operational metrics. Verify duplicate webhooks, failed payments, expiry, cross-account IDs, mismatched content, and Discovery downtime cannot change money, ownership, or playback outcomes.

## Release gate

Run focused domain/application tests as slices land, PostgreSQL migration and repository integration tests, API contract tests, frontend build/tests, Spotless, and the full backend verification gate. The user will handle Docker image rebuild and smoke testing. Compare controlled fixtures proving Top and Trending differ, then enable the collection flag after active snapshot and data-quality checks pass.
