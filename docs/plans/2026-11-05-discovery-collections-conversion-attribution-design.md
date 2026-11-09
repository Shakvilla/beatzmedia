# WU-DIS-4 — Discovery Collections and Conversion Attribution

**Status:** Approved design
**Date:** 2026-11-05

## Outcome and scope

Ship public, explainable Top, Trending, Emerging, genre, Ghana, music, and podcast collections. Keep singles, albums, EPs, shows, and applicable episodes as their published entities. Label organic, editorial, and sponsored positions. Measure the journey from collection impression through listening, purchase, or creator support without changing payment or playback authority. Ghana (`GH`) is the launch market; market keys can expand later. Sponsored placement controls are in scope; advertiser billing and campaign budgets are not.

## Decision and existing system

Use scheduled, versioned ranking snapshots. Request-time aggregation would repeatedly scan engagement data and make ranks hard to reproduce. Pure editorial curation cannot honestly power Top, Trending, or Emerging. Today `/v1/home` sorts catalog play counts into Trending and Top; analytics has artist-level rollups, not the content-level signals needed here. The existing admin featured slot has an `isSponsored` flag but no content reference or schedule. WU-DIS-3 provides account-bound recommendation attribution and 90-day raw-event retention, which this unit extends for collections.

Catalog and podcasts own publication and metadata. Playback and engagement own listening facts; library owns saves and follows; commerce and podcast support own money outcomes. Discovery owns collection policy, ranking metrics, snapshots, placements, and attribution aggregates. Cross-module reads use narrow input ports or durable events, not another module's tables.

## Collections and ranking

Each ranking formula has a named version, window, eligibility thresholds, component totals, and stable tie-breaker. Only published, available content enters a snapshot. Deduplicate child tracks under an album or EP when a release card represents that content. Music and podcast taxonomies are distinct.

| Collection | Meaning | Primary signals |
| --- | --- | --- |
| Top | Sustained popularity over 28 days | qualified plays, unique listeners, completion, saves, follows, purchase/support outcomes |
| Trending | Recent acceleration | last 24 hours versus preceding seven-day baseline, freshness, unique listeners, completion, saves |
| Emerging | Growth before broad reach | recent listener growth, completion, saves, follower conversion under historical reach ceilings |
| Genre | Eligible content within one canonical taxonomy term | the appropriate music or podcast ranking policy |
| Ghana | Content with explicit `GH` association | same policy within the Ghana market |
| Music / Podcasts | Browsable media groupings | grouped music releases / shows and relevant episodes |

Exact weights and minimum thresholds are configuration of the versioned formula, calibrated against seeded data before launch. A candidate with insufficient unique listeners does not appear as a chart leader merely because of a few repeated plays. Duplicates, implausible replay bursts, very short starts, and creator self-activity are excluded or capped. Missing signal families contribute zero with a recorded data-quality flag; they are never silently represented as measured completion or unique listeners. No IP-based market inference.

## Snapshot and API

A scheduled job reads bounded content-level aggregates, builds an inactive version, validates uniqueness, eligibility, position continuity, and minimum viable result size, then atomically activates it. Failure leaves the previous active version intact and emits an operational alert. Keep a bounded history for audit and rollback. Reads use the latest active snapshot, with explicit empty-state responses if no valid version exists; a stale snapshot is marked internally and monitored.

`GET /v1/discovery/collections?market=GH&media=all` returns collection summaries. `GET /v1/discovery/collections/{slug}?market=GH&cursor=&limit=` returns a cursor page tied to one snapshot version, so paging cannot mix rankings. Unknown slug, unsupported market, malformed cursor, and expired version have explicit 4xx responses; the client can restart pagination after a version expires. Each card carries content kind and ID, grouped display metadata, rank, reason, formula and snapshot version, market, placement type, and optional opaque attribution ID. The Home feed initially adapts collection results to its existing response shape, with frontend collection pages adopting the richer contract incrementally.

## Editorial and sponsored placements

Extend admin editorial configuration to reference a content kind and ID, target collection and market, schedule, requested position, and `editorial` or `sponsored` type. Validate the reference, schedule, RBAC, and audit trail on mutation. Merge active placements into reserved positions after the organic snapshot is read. Omit content that becomes unavailable; deduplicate against organic results, preserving a bounded organic share and stable positions. The API and UI explicitly label sponsored cards. Editorial and sponsored entries cannot alter organic scores or be reported as organic rank wins.

## Attribution and conversion

Extend the WU-DIS-3 attribution model with surface, collection slug/version, market, rank, placement type, and content reference. An opaque account-bound ID supports idempotent impression and open events, then optional listening outcomes. Web navigation holds short-lived attribution context and passes it only when the opened or played item matches the card.

Cart addition preserves optional matching context on the cart line; checkout copies it onto the immutable order line. On confirmed settlement, an idempotent discovery observer records purchase conversion for matching content, parent release, show, or creator within seven days of the touch. Creator support follows the same account-bound matching and records a conversion only after authoritative payment success. Failed, canceled, refunded, or duplicate payments do not count as new successful conversions; refund adjustment is represented separately so reporting can show net outcomes. Never use attribution as evidence for a charge, ownership grant, payout, or royalty amount.

Raw attribution and event facts expire after 90 days; privacy-safe collection/version aggregates persist for comparison. Creator-facing reporting, if exposed later, uses aggregate thresholds and cannot reveal account activity. Invalid, expired, mismatched, or unavailable attribution becomes unattributed activity. Discovery write failure cannot block a listening checkpoint, cart mutation, checkout, settlement, ownership grant, support payment, or payout. Failures are observable and retryable where durable events are available.

## Verification and release criteria

Unit tests prove formulas distinguish sustained popularity from acceleration and emerging growth, use stable ties, enforce unique-listener minimums and replay caps, and preserve release/show grouping. Integration tests prove snapshot activation is atomic, rollback serves the previous version, pagination is version-consistent, unpublished content and expired placements disappear, and sponsored labeling survives Home and collection APIs. Contract and frontend tests cover each card kind, visible placement labels, accessible navigation, empty/error states, and attribution carried only for matching content.

End-to-end scenarios cover impression → open → qualified listen, purchase settlement, creator support settlement, duplicate webhook delivery, failed/refunded payment, account isolation, attribution expiry, and discovery outages. Compare Top and Trending on controlled fixtures to show that their ordering differs for the intended reasons. Inspect query plans and job duration on realistic data; keep reads bounded and indexed. Roll out behind a flag, observe active version age, job failures, missing metrics, and conversion counts, and retain a rollback path to the prior snapshot and existing Home feed.

## Delivery boundaries

First establish reliable content-level metrics and snapshot generation; then expose read APIs and Home compatibility; then add editorial placement controls and frontend collections; finally connect purchase and support conversions and aggregate reporting. This sequence keeps each slice testable. A full ad marketplace, multi-country data ingestion, machine learning ranking, and creator-level account activity reports remain outside WU-DIS-4.
