# Frontend Admin Editorial Wiring — Design

**Date:** 2026-07-25
**Slice:** Admin console — slice 6 of 6, the last one (editorial: featured slots, push schedule, curated playlists).
**Branch:** `feat/frontend-admin-editorial` off `master`. **Independent** — unlike slices 4/5 it needs nothing from the unmerged PRs (#167/#168), so it can merge on its own.
**Backend:** `AdminEditorialResource` (`admin` module) — already merged. No backend change.

## Goal

Wire `admin.editorial` from the `admin-data` mock to the live endpoints, with **no visual change**, and make the featured-slot reordering and removal genuinely persist.

## Verified backend surface

Read from the source directly.

**`AdminEditorialResource`**, base `/v1/admin/editorial`. Reads accept `{super-admin, editor, support}`; **writes accept `{super-admin, editor}` only** (a `support` admin can view this page but not change it).

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/featured` | — | `List<FeaturedSlotDto>` |
| PUT | `/featured` | `List<FeaturedSlotRequest>` | `List<FeaturedSlotDto>` — **full ordered replace**; 422 on a blank title or duplicate id |
| GET | `/push` | — | `List<PushItemDto>` |
| POST | `/push` | `PushItemRequest` | `PushItemDto` (201) |
| GET | `/playlists` | — | `List<CuratedPlaylistDto>` |
| POST | `/playlists` | `{ name }` | `CuratedPlaylistDto` (201) |

- `FeaturedSlotDto = { id, title, note, sponsored: boolean }` — **1:1** with the mock's `FeaturedSlot` (whose `sponsored` is optional; the wire always sends a boolean).
- `PushItemDto = { id, day, timeLabel, title, audience, scheduledAt }` — the mock's field is `time`, so the mapper renames `timeLabel → time`. `scheduledAt` (nullable ISO) is **served but not rendered** — the row shows the cosmetic `timeLabel` and a hardcoded "scheduled" pill.
- `CuratedPlaylistDto = { id, name }` — **1:1**.
- No money anywhere on this resource. The only timestamp is the unused `scheduledAt`.

## Architecture

- **New `Frontend/src/lib/api/queries/admin-editorial.ts`:**
  - `featuredQuery()` → `GET /featured` → `toFeaturedSlot[]`. Key `['admin','editorial','featured']`.
  - `pushScheduleQuery()` → `GET /push` → `toPushItem[]`. Key `['admin','editorial','push']`.
  - `curatedPlaylistsQuery()` → `GET /playlists` → `toCuratedPlaylist[]`. Key `['admin','editorial','playlists']`.
  - `apiSaveFeatured(slots)` → `PUT /featured` with the **full ordered list**, returning the saved order.
- **Mappers** (`lib/api/mappers.ts`): `toFeaturedSlot`, `toPushItem` (the `timeLabel → time` rename), `toCuratedPlaylist`, plus `*Wire` types and a `toFeaturedSlotRequest` for the PUT body.
- **Route** (`admin.editorial.tsx`): three `useQuery` reads replacing the single `getEditorial()` memo, `AdminLoadError` on error and a loading branch, and the featured list's local `useState` replaced by server data.

### How reorder and remove persist

The endpoint is a **whole-list replace**, so both actions compute the new ordered array locally and `PUT` it, then invalidate. This preserves the existing UI exactly — the same menu items, the same optimistic feel — while making the result durable.

Because a replace is destructive, each write sends the list the user is actually looking at, and on failure the query is invalidated so the UI snaps back to the server's truth rather than silently keeping a local edit that never landed. The menu buttons are disabled while a save is in flight, so two rapid reorders cannot race and clobber each other.

## No visual change, with one correction

This slice preserves the UI exactly. The single behavioral correction:

- **"Removed from featured" currently toasts success for something that never persists** — the row disappears from local state and returns on reload. After wiring, that toast reflects a real, durable change. Same for reorder, which today doesn't even toast.

Everything else — every class, every copy string, the drag-to-reorder hint, the "scheduled" pill, the coverGradient art — is untouched.

## Out of scope / Category B (stays unwired)

- **"New playlist"** and **"Schedule push"** keep their existing informational toasts. Both endpoints exist, but neither button has a form, and building one is a product-design task (field layout, validation, the audience vocabulary), not wiring. Note their current toasts read as *prompts* ("New playlist — pick tracks to curate", "Schedule a new push notification"), not as false success claims, so leaving them is honest.
- **"Replace" on a featured slot** — no endpoint exists at all. Permanent Category B.
- **Opening a curated playlist** — no detail endpoint or route. Stays a toast.
- `PushItemDto.scheduledAt` — served but not rendered, matching the mock.
- The push row's hardcoded "scheduled" pill — there is no status field on the wire to drive it.

## Testing & gate

- Co-located Vitest: `admin-editorial.test.ts` (the three query URLs + keys; the PUT's URL and that its body is the **full ordered array** in the given order) and mapper cases in `mappers.test.ts` (the `timeLabel → time` rename, the 1:1 passthroughs, and `toFeaturedSlotRequest` shaping).
- A test named for a behavior must assert that behavior.
- Gate (from `Frontend/`, Node 22 via nvm): `npm run build` (`tsc -b`) + the full `npx vitest run` green.
- **Live QA needs `super-admin` or `editor`** — a `support` admin can read the page but every write 403s. Verify: the three sections load; a reorder survives a reload; a removal survives a reload; a failed save snaps back to server truth rather than keeping a phantom local edit.
- One PR: `feat/frontend-admin-editorial`, targeting `master` directly.
