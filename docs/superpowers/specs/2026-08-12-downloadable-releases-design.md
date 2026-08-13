# Downloadable releases — design

**Status:** approved, not yet planned
**Date:** 2026-08-12
**Supersedes:** the "missing download path" entry in `docs/qa/2026-08-08-admin-gap-report.md` §8 (GAP-30)

## Problem

BeatzClik is a **buy-to-own** platform. The post-purchase screen says *"yours forever"* under a
**"Download all"** button, the store's Hi-Fi tab sells *"Studio-grade masters, downloaded and owned
forever"*, and Settings offers a *"Download quality"* selector.

None of it is real. There is no download endpoint anywhere in the API. Media serves signed,
time-boxed **stream** URLs; nothing hands over a file. The button had no `onClick` at all until
GAP-30 disabled it.

Separately, artists have no say in the matter. Some will not want their masters distributable, and
today the platform neither offers them the choice nor honours one.

## What this delivers

An artist decides, per release, whether buyers may download the audio. When they allow it, buyers get
a real lossless file. When they don't, the release is stream-only and every surface says so before
anyone pays.

**Not in this spec:** in-app offline playback (no service worker, cache, or IndexedDB exists — it is
a subsystem of its own), and the one-click ZIP bundle (see *Follow-on work*).

## Decisions

Seven decisions were settled during design. Each is recorded with its reasoning because the reasoning
is what a future reader will need.

### 1. Granularity: per release

One column on `release`, beside price and visibility. Per-artist would be too blunt (an artist may
feel differently about a mixtape than a master); per-track too fine (nobody wants to answer this
twelve times, and a half-downloadable album is a support ticket).

### 2. The permission is captured at purchase, not read live

`ownership_grant.downloadable` is copied from the release at settlement and never updated.

An artist switching downloads off affects **future sales only**. This follows the precedent already
set in the PRD's **OQ-8**:

> Buy-to-own means owners must retain access. *Default:* deleting/taking-down a release **unlists** it
> but preserves existing owners' access **and downloads**.

If a *takedown* — a more adversarial event than changing one's mind — does not strip download rights
from people who paid, a preference flip must not either. The cost is that an artist who regrets
allowing downloads cannot claw back what is already sold. That is correct, for the same reason you
cannot un-sell a record.

### 3. No default — the artist must choose

`release.downloadable` is nullable so a draft can hold "unanswered", and `PublishRelease` rejects
`NULL`. A default would mean inertia decides: off-by-default quietly makes the platform
stream-only, on-by-default means "the artist should choose" degrades to "the artist should notice",
and the artists who most want it off are the least likely to find a switch.

The cost is one required field in the publish flow. For a decision about who may keep copies of your
work, that friction is warranted.

### 4. Disclosed wherever the release is sold

Because the permission is fixed at purchase, it is part of what someone is buying. A marker appears
on release/track/album detail and in the cart — *"Download available"* / *"Streaming only"*.

This forces a copy fix: the Hi-Fi tab's *"Studio-grade masters, downloaded and owned forever"* becomes
false the moment any Hi-Fi artist opts out. The tier keeps its lossless promise; it stops promising
downloads universally.

Requiring downloads-on for Hi-Fi listings was considered and rejected: that makes it a tier rule, not
an artist choice, in exactly the tier where an artist is most likely to want the choice.

### 5. Downloads deliver a new lossless FLAC rendition

The pipeline currently produces only AAC 128k (`full.m4a`) and the 30s `preview.m4a`. Serving
`full.m4a` as "the download" would hand over a 128 kbps file on a platform advertising lossless
masters, and would leave Settings' *"Lossless"* option still unbacked.

Serving the artist's original upload was considered — no transcode needed, truest to buy-to-own — but
rejected: it hands out the master itself, formats vary per track, and sizes are unbounded.

A normalised FLAC delivery rendition costs a pipeline stage and storage, and is the only option that
makes the existing "lossless" copy true.

### 6. Server-built ZIP for multi-track purchases

Deferred to spec B, but decided here so spec A does not foreclose it: the download endpoint's shape
must allow a bundle without breaking clients.

### 7. Settings' "Download quality" selector is removed

It offers *High (256) / Very high (320) / Lossless* as a **fan** preference. With one delivery format
there is nothing to choose, so it is a control that does nothing — the GAP-19 class. The hardcoded
*"Downloads — N tracks · 1.4 GB"* figure goes with it; the byte count is invented.

## Data model

Three additive columns. Versions are allocated at implementation time with
`backend/scripts/next-migration-version.sh` (next free is currently 979) — not hardcoded here,
because other work may land first.

```sql
-- catalog
ALTER TABLE release ADD COLUMN downloadable boolean;

-- commerce
ALTER TABLE ownership_grant ADD COLUMN downloadable boolean NOT NULL DEFAULT false;

-- media
ALTER TABLE media_asset ADD COLUMN lossless_key varchar(255);
```

`ownership_grant.downloadable` takes `DEFAULT false` so the column can be `NOT NULL` on a table that
already exists. Every write sets it explicitly from the release, so the default is a migration
artifact — but it is deliberately `false` rather than `true`: if a future insert path ever forgets to
set it, the grant denies the download rather than granting one the artist never agreed to. Fail
closed, for the same reason the payment-rail flags do.

There are **zero grants today and nothing in production**, so the default never applies to real data.

`release.downloadable` deliberately has no default and stays nullable: `NULL` means "not yet chosen",
which is the state the publish guard exists to reject.

## Components

### Media — the lossless rendition

`DeliveryVariant` gains `LOSSLESS` (today: `FULL`, `PREVIEW`). The transcoder produces
`lossless.flac` from the original alongside the existing renditions:

```
ffmpeg -i <original> -c:a flac -compression_level 8 lossless.flac
```

FLAC is chosen over WAV for roughly half the size at identical fidelity, and over ALAC for tooling
ubiquity.

An asset whose `lossless_key` is null answers `409 DOWNLOAD_NOT_READY` rather than silently serving
the AAC. **Backfill is out of scope**: one dev release, zero grants, nothing in production. If that
changes before this ships, a re-transcode pass becomes a prerequisite.

### Commerce — capturing the permission

At settlement, `GrantOwnership` reads the release's `downloadable` and writes it onto each grant it
creates. Album purchases expand to per-track grants (INV-2); every expanded grant carries the same
value, captured once from the parent release.

### Catalog — the choice and its guard

`PublishRelease` rejects `downloadable IS NULL` with `422 DOWNLOAD_CHOICE_REQUIRED`. This is the
mechanism behind decision 3 — without it, "required choice" is a UI convention that any other client
can skip.

### The endpoint

```
GET /v1/tracks/{id}/download
→ 200 { downloadUrl, expiresAt, format: "flac", sizeBytes }
```

Guards, in order, each with a distinct failure:

| Condition | Failure |
|---|---|
| authenticated | `401` |
| caller owns this track | `403 NOT_OWNED` |
| the caller's grant permits download | `409 DOWNLOAD_NOT_ALLOWED` |
| a lossless rendition exists | `409 DOWNLOAD_NOT_READY` |

The URL is signed with a 5-minute TTL and `Content-Disposition: attachment`, filename
`<Artist> - <Title>.flac`.

**The grant is the authority, not the release.** Checking `release.downloadable` here would silently
undo decision 2 — an artist toggling the flag would retract downloads from people who already paid.
This is the single most likely implementation mistake in the whole spec.

**The server is the authority, not the client.** A UI that hides the button is a convenience, exactly
as with INV-3 previews.

### Frontend

| Surface | Change |
|---|---|
| Studio release wizard, Details step | Required choice, no pre-selection; blocks publish |
| Studio release detail | Editable, with copy stating it affects *future* sales only |
| Track / album / release detail, cart | "Download available" / "Streaming only" marker |
| Store Hi-Fi tab | Subtitle stops promising downloads universally |
| Library | Per-track download action when the grant permits |
| Settings | Remove "Download quality"; remove the invented "1.4 GB" |
| `checkout.complete` | "Download all" stays disabled until spec B |

## Invariant

**A download is served only when the caller's own grant permits it.**

This mirrors INV-3's shape — server-enforced, client-independent — and should be numbered in
`BACKEND-PRD.md` §3.3 alongside it when this is planned.

## Testing

| Level | Covers |
|---|---|
| Unit | Publish guard rejects `NULL`; grant capture copies the release value; the download policy's decision table |
| Integration | The four-row guard table above, each asserted separately — particularly that flipping `release.downloadable` to false leaves an existing grant's download working |
| Media IT | `RealTranscodeIT` extended: the FLAC rendition exists, and its duration matches the source (a lossless rendition that is silently clipped would otherwise pass) |
| Contract | The endpoint added to `API-CONTRACT.md` |

The grandfathering test is the one that matters most: it is the only executable statement of decision
2, and decision 2 is the one a later "simplification" is most likely to undo.

**Caveat:** per issue #210 the `contract-test` gate currently runs zero tests, so the contract entry
will not be mechanically verified. Not this spec's problem to fix, but worth knowing before treating
a green gate as proof.

## Follow-on work

**Spec B — ZIP bundling.** One-click "Download all" for multi-track purchases. Needs an async job
runner, temp storage for multi-GB archives, a progress path, a cleanup policy, and auth on a
long-lived artifact. Depends on this spec.

It will be a **separate resource**, not a variant of the per-track endpoint —
`POST /v1/orders/{id}/download` returning a job handle, since a bundle is asynchronous and a single
file is not, and pretending otherwise would force a synchronous endpoint to grow a polling protocol.
Nothing in this spec needs to change to accommodate it; the per-track endpoint stays exactly as
specified and remains the thing spec B builds on top of.

**In-app offline playback.** Independent of downloads and currently non-existent. Worth stating
plainly when it is specced: offline playback puts the bytes on the device, so without real DRM
"plays offline but cannot be downloaded" is a soft distinction, not a security boundary. This spec
avoids that claim entirely — it controls whether a download is *offered*, nothing more.
