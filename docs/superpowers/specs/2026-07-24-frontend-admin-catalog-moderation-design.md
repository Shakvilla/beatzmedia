# Frontend Admin Catalog & Moderation Wiring — Design

**Date:** 2026-07-24
**Slice:** Admin console — slice 3 of ~6 (catalog list + detail, moderation queue). Editorial deferred to its own later slice.
**Branch:** `feat/frontend-admin-catalog-moderation` off `master` (post-#165, so `format.ts` already has `relativeTime`/`relativeTimeAgo`/`formatDuration`).
**Backend:** `AdminCatalogResource` + `AdminModerationResource` — already merged. No backend change.

## Goal

Wire three admin screens — the catalog list (`admin.catalog`), catalog item detail (`admin.catalog.$itemId`), and moderation queue (`admin.moderation`) — from the `admin-data` mock to the live endpoints, with **no visual change**. Reuses the `queries/admin-*` + shared-mapper + `AdminLoadError` pattern established by the Users slice (#165).

## Context

### Catalog list — `admin.catalog.tsx`
Two-part screen: a header summary (`CATALOG_SUMMARY = {artists, albums, tracks}` — **Category B, no endpoint, stays mock**) and a filterable table over `getCatalog(): CatalogItem[]`.
- `CatalogItem = { id, title, note?, artist, type: CatalogType, tracks: number, status: CatalogStatus }` where `CatalogType = 'Album'|'Single'|'EP'|'Compilation'|'Mixtape'`, `CatalogStatus = 'pending'|'flagged'|'published'|'takedown'`.
- `CATALOG_COUNTS = { pending, published, takedown }` drives filter-chip counts.
- Filters: `pending | published | takedown | all` (default `pending`); **the `pending` chip includes `flagged` items** (client-side bucketing). Search over title/artist. Checkbox multi-select + `usePaged` pagination.
- Actions: row **Approve** → status `published`; menu **Flag** → `flagged`; menu **Take down** → `takedown` (list has **no** reason prompt); **Bulk approve** (selection bar); menu **View details** → navigate to detail.

### Catalog detail — `admin.catalog.$itemId.tsx`
Today it finds the item in `getCatalog()` and **fabricates** the tracklist (fake ISRC/duration), splits (hardcoded 70/20/10), and an action log client-side. The real `CatalogItemDetailDto` supplies these for real.
- Rendered per track: **title, ISRC, duration** (a Play button that only toasts — **Category B**). **Price is NOT rendered** by the mock.
- Splits render `{ name, role, pct }`. Header shows `{artist} · {type} · {N} tracks · {note}`. A metadata block + an action-history list.
- Actions: **Approve** → published; **Flag** → flagged; **Take down** → opens a reason modal (Copyright claim / Metadata mismatch / Duplicate ISRC / Policy violation / Other + free text) → `takedown`. Each also appends to the local action log. There is **no Reinstate button** and we are **not adding one** (the endpoint exists but strict no-visual-change wins).

### Moderation queue — `admin.moderation.tsx`
Table over `getModerationQueue(): ModerationItem[]` with a header summary.
- `ModerationItem = { id, item, reporter, reason: ModReason, age: string, severity: ModSeverity, status: ModStatus }`; `ModStatus = 'open'|'in_review'|'resolved'`, `ModSeverity='high'|'med'|'low'`, `ModReason` one of Copyright/Hate speech/Sexual content/Spam/Impersonation.
- Header: `MOD_SLA_HOURS`, `MOD_ESCALATED`, and an open-count.
- Tabs: status `open|in_review|resolved|all` (default `open`); reason-type chip filter. Pagination.
- Actions: **Review** → `in_review`; menu **Approve & keep** → `resolved`; menu **Remove content** → `resolved` (**no** reason collected in the UI); menu **Escalate** → today a `toast(...)` only (does NOT change local state); menu **Dismiss report** → `resolved`.

### Backend surface

**`AdminCatalogResource`** (base `/v1/admin/catalog`, `@RolesAllowed` super-admin/moderator[/support for reads]):
| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/?status=&q=&page=&size=` | — | `PagedCatalogDto` |
| GET | `/{id}` | — | `CatalogItemDetailDto` |
| POST | `/{id}/approve` | `{ goLiveAt? }` (ISO) | `CatalogItemDetailDto` |
| POST | `/{id}/flag` | `{ note? }` | `CatalogItemDetailDto` |
| POST | `/{id}/takedown` | `{ reason }` (non-blank) | `CatalogItemDetailDto` |
| POST | `/{id}/reinstate` | — | `CatalogItemDetailDto` (**unused this slice**) |

- `PagedCatalogDto = { items: CatalogItemDto[], page, size, total, counts: { pending, published, takedown } }`.
- `CatalogItemDto = { id, title, note, artist, type, tracks: int, status }` — 1:1 with mock `CatalogItem`.
- `CatalogItemDetailDto = { id, title, note, artist, type, status, upc, tracklist: TrackDto[], splits: SplitDto[], actionLog: ActionLogEntryDto[] }`; `TrackDto = { position, trackId, title, isrc, durationSec, priceMinor }`; `SplitDto = { trackId, name, role, percent, confirmation }`; `ActionLogEntryDto = { id, action, by, time }` where **`time` is ISO-8601** (`Instant.toString()`).

**`AdminModerationResource`** (base `/v1/admin/moderation`):
| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/?status=&type=&page=&size=` | — | `ModerationQueueDto` |
| POST | `/{id}/review` | — | `ModerationCaseDto` |
| POST | `/{id}/approve` | — | `ModerationCaseDto` |
| POST | `/{id}/remove` | `{ reason? }` | `ModerationCaseDto` |
| POST | `/{id}/escalate` | — | `ModerationCaseDto` |
| POST | `/{id}/dismiss` | — | `ModerationCaseDto` |

- `ModerationQueueDto = { items: ModerationCaseDto[], page, size, total, summary: { openCount, slaHours, escalatedCount } }`.
- `ModerationCaseDto = { id, item, reporter, reason, time, severity, status, escalated: boolean }` — **`time` is ISO-8601** (replaces the mock's cosmetic `age` string); `reason` is the same set of values as `ModReason`; `escalated` is new.

## Architecture

Same idiom as the merged studio/Users slices; `apiFetch` (`lib/api/client.ts`) prepends `/v1` and needs no change.

- **New `Frontend/src/lib/api/queries/admin-catalog.ts`:**
  - `catalogQuery(status)` → `GET /admin/catalog?size=100&status=` (`status` omitted when `'all'`) → `toCatalogList` (items + counts). The status tab is now sent server-side; client-side free-text search stays in the route, and the client-side status filter remains as a harmless safety net over the already-filtered page. Key `['admin','catalog','list', status]`.
  - `catalogItemQuery(id)` → `GET /admin/catalog/{id}` → `toCatalogDetail`. Key `['admin','catalog','detail', id]`.
  - `apiApproveCatalog(id)` / `apiFlagCatalog(id, note?)` / `apiTakedownCatalog(id, reason)` → the POSTs, `Promise<void>`, invalidated by the routes.
- **New `Frontend/src/lib/api/queries/admin-moderation.ts`:**
  - `moderationQuery(status, type)` → `GET /admin/moderation?size=100&status=&type=` (each omitted when `'all'`) → `toModerationQueue` (items + summary). Both the status tab and the reason-type chip are sent server-side. Key `['admin','moderation','queue', status, type]`.
  - `apiReviewCase(id)` / `apiApproveCase(id)` / `apiRemoveCase(id, reason?)` / `apiEscalateCase(id)` / `apiDismissCase(id)` → `Promise<void>`.
- **Mappers (`lib/api/mappers.ts`):**
  - `toCatalogItem` (1:1, narrow `type`/`status` unions), `toCatalogList` (items + counts + a computed `all` total for the chip).
  - `toCatalogDetail`: header fields + `tracklist` mapped to `{ position, title, isrc, duration }` where `duration = formatDuration(durationSec)` (**`priceMinor` is intentionally not surfaced — the mock renders no price**); `splits` → `{ name, role, pct: percent }`; `actionLog` → `{ id, action, by, time: relativeTimeAgo(wire.time) }`.
  - `toModerationCase`: `{ id, item, reporter, reason, severity, status, age: relativeTime(wire.time) }` (relative, no "ago" — matches the mock's "6h" style; `escalated` mapped but not rendered).
  - `toModerationQueue`: items + `summary` → `{ open: openCount, sla: slaHours, escalated: escalatedCount }`.
  - Plus `*Wire` types for every DTO above.
- **Routes:** replace each `useState(getX())` with `useQuery(...)` (default `[]` / zeroed summary while loading); the actions become async handlers that `await` the mutation, `await queryClient.invalidateQueries(...)`, and toast success/`'error'`. `AdminLoadError` renders on `isError`. All JSX/classes/copy preserved; the two Category-B toasts (catalog Preview, catalog header summary) and the moderation Escalate toast copy are unchanged (Escalate now also persists server-side).

## Out of scope / Category B (unchanged)

- Free-text `q` search is still client-side over the fetched page (no server-side search endpoint), and there is still no true server-side pagination — the fetch is capped at `size=100` (the backend's `PageRequest.MAX_SIZE`), so a catalog or moderation queue larger than that is not fully reachable from these screens yet. (`status` and moderation's `type`, however, ARE now sent server-side — see Architecture above.)
- Catalog list header summary (`CATALOG_SUMMARY`) — no endpoint; stays mock.
- Catalog detail track **Preview/Play** — no endpoint; stays a toast.
- Catalog **Reinstate** — endpoint exists but no button; not added (no visual change).
- Catalog **bulk approve** — no bulk endpoint; wired as `Promise.all` of `apiApproveCatalog` (matches Users bulk-suspend).
- Track **price** — served (`priceMinor`) but never rendered by the mock; not surfaced.
- Moderation **Remove** reason — the UI collects none; send `apiRemoveCase(id)` with no reason (backend `reason` is optional).
- Editorial (`admin.editorial`) — entirely deferred to its own slice.
- The "Rights & splits" panel is per-release but the backend serves splits per-track (`SplitDto.trackId`), so `toCatalogDetail` dedupes them by `name|role|percent`. When a release's tracks genuinely carry different percentages for the same collaborator, the merged list keeps both rows and can sum past 100%. A true per-track rights view is out of scope for this slice.

## Testing & gate

- Co-located Vitest: `admin-catalog.test.ts` and `admin-moderation.test.ts` (query URLs + keys; mutation URLs + bodies), plus mapper cases in `mappers.test.ts` (union narrowing; `duration`/`age`/`actionLog time` formatting; counts/summary projection).
- No visual change: JSX/classes/copy preserved; only the data source + the wired actions change. Two deliberate, small additions: `upc`/`isrc` are always `null` from the backend today (Category B), so the detail renders an em-dash placeholder rather than a fabricated value; and both list routes gained a "Loading…" branch (previously they flashed their empty state on first paint before any data existed).
- Mapper outputs match `admin-data.ts` types exactly (`CatalogItem`, `CatalogStatus`, `CatalogType`, `ModerationItem`, `ModStatus`, `ModSeverity`, `ModReason`).
- Distinct `AdminLoadError` affordance on query failure (the admin-wide standard from #165).
- Gate (from `Frontend/`, Node 22 via nvm): `npm run build` (`tsc -b`) + `npx vitest run` green; no NEW lint errors. Live QA signed in as an admin: list loads with live counts; approve/flag/takedown/bulk persist; detail loads real tracklist/splits/log; takedown-with-reason persists; moderation review/approve/remove/escalate/dismiss persist; a forced query failure shows `AdminLoadError`. One PR: `feat/frontend-admin-catalog-moderation`.

## Note

The Users slice's in-flight-guard follow-up (disable action buttons while a mutation is pending) is being handled separately. This slice matches the currently-shipped Users pattern (async handler + toasts, no disabled guard); if that follow-up lands a shared guard utility before this slice's routes are wired, adopt it here for consistency.
