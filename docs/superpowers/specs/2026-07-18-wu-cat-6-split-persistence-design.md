# WU-CAT-6 — Split Persistence + Finalize Validation (design)

**Date:** 2026-07-18
**Area:** Backend (`backend/`, catalog module) — release-draft split write path
**Consumes:** WU-CAT-5 draft flow (`CreateReleaseDraft` / `UpdateRelease` / `FinalizeRelease`), the
`split_entry` table (V305), and the WU-CAT-4 `hasPendingSplits` go-live guard — all already shipped.
**Status:** design approved; ready for implementation plan

## Goal

Make per-track royalty splits real. Persist collaborator splits through the wizard's existing draft
`PATCH`, read them back on every release view, and validate the per-track split sum at finalize — so
the already-shipped `hasPendingSplits` go-live guard and the downstream payout subdivision
(LLFR-PAYMENTS-02.1) finally have real `split_entry` data to act on. Backend-only; no new module.

## Program context (this is sub-project #1 of 4)

"Full collaborator-confirmation flow" is a program, not one WU. This spec covers **only the
foundation** — split persistence. The remaining pieces each get their own spec → plan → implement
cycle and are **non-goals here**:

1. **WU-CAT-6 (this spec)** — split persistence + finalize validation. *catalog.* No dependencies.
2. **Collaborator invite + accept flow** — `split_invite` token model (mirrors identity's
   `password_reset_token`), invite sent via the notifications `Mailer`, a **public** tokenized
   `POST /v1/collab/splits/accept` endpoint (`pending → confirmed`), resend, expiry sweep, and a
   `declined` decision. *catalog + notifications.* Depends on #1.
3. **Frontend Splits-step wiring** — persist splits through the wizard `PATCH`, read back
   confirmation states, real "Resend invite", keep the client 100% gate. *Frontend.* Depends on #1
   (real UX needs #2).
4. **Frontend collaborator-accept page** — public route reached from the invite email. *Frontend.*
   Depends on #2.

## Locked decisions

1. **Implicit remainder.** `split_entry` holds **only collaborator** rows (real name/email/role). The
   creator's share = `100 − Σ(collaborators)` and is **never stored** — matching INV-12's "the
   originating creator holds the remainder implicitly." This avoids an `email='me'` sentinel in a
   `NOT NULL` column and keeps every `split_entry` row a thing that genuinely needs confirming. The
   later frontend-wiring slice (#3) strips the wizard's `self` entry before PATCH.
2. **Interim confirmation state = `pending`.** Every collaborator persisted by WU-CAT-6 is stored
   `confirmation = 'pending'`. Solo/self-only releases (no collaborator rows) go live normally.
   Releases *with* collaborators reach `in_review` but cannot go `live` until the invite/accept flow
   (#2) confirms them — the correct invariant behavior, already enforced by the existing
   `hasPendingSplits` guard with **no new go-live code**. No window of fake confirmations.
3. **Splits nested in `tracks[]`.** The PATCH carries splits inside each track entry, not as a
   separate map — one atomic track+splits replace-set, one round-trip, and a split structurally
   cannot reference a track absent from the release.

## Backend contract (additive to WU-CAT-5)

`UpdateRelease` input port (`UpdateReleaseCommand`) — `TrackRef` gains a nested split list:

```java
record TrackRef(String trackId, int position, long priceMinor, List<SplitRef> splits) {}
record SplitRef(String name, String email, String role, int percent) {}
```

- `SplitRef` carries **collaborators only** (no `self`, per decision 1). No `confirmation` on input —
  the service sets it to `pending` (decision 2).
- Replace semantics parallel the existing `tracks` list: for a track whose `splits` is **null**,
  that track's existing splits are **untouched**; a **non-null** list (incl. empty `[]`) **replaces**
  that track's collaborator set wholesale. (`tracks == null` still leaves the whole track list — and
  therefore all splits — untouched.)

`TrackDraftView` gains the read-back list, returned by `GET /:id` and every mutating draft endpoint:

```java
record TrackDraftView(String trackId, String title, int duration, String status,
                      int position, MoneyView price, List<SplitView> splits) {}
record SplitView(String id, String name, String email, String role, int percent, String confirmation) {}
```

- `confirmation` serializes the domain `SplitConfirmation` (`self | confirmed | pending | auto`); in
  this WU persisted collaborators are always `pending`.

Endpoints, methods, and paths are unchanged — this is purely additive fields on the existing
WU-CAT-5 `PATCH`/`GET` shapes. **API-CONTRACT.md** is updated to document `splits` on the track
draft shape (previously "no `splits` yet — deferred to WU-CAT-6").

## Domain & persistence

- The `Release` aggregate already models `SplitEntry` (`id`, `trackId`, `name`, `email`, `role`,
  `percent`, `confirmation`); WU-CAT-6 wires the **write path** the aggregate lacked. `UpdateRelease`
  applies each track's incoming `SplitRef` list to that track (draft-only, like the other PATCH
  fields — a `409 ILLEGAL_TRANSITION` on a non-draft release, reusing the existing guard).
- **Outbound persistence (replace-set):** for each track with a non-null `splits`, delete that
  track's existing `split_entry` rows, then insert the new set — each row `confirmation = 'pending'`,
  a freshly generated `id`. `ON DELETE CASCADE` on the `track_id` FK already cleans splits when a
  track is removed.
- **No new migration expected.** `split_entry` (PK `id`; `track_id` FK `ON DELETE CASCADE`;
  `percent INT CHECK (percent BETWEEN 0 AND 100)`; `confirmation TEXT CHECK (... 'pending' ...)`)
  and `idx_split_track` all shipped in **V305**. Confirm with
  `backend/scripts/next-migration-version.sh`; add a migration only if an index/column proves
  necessary (not anticipated). "No migration" is a legitimate DoD outcome here.

## Validation, invariants & audit

- **Per-row bounds** are validated in the application layer (`percent` 0–100) so a bad value returns
  a `422` via the standard field-validation envelope (**not** `SPLIT_OVER_100`, which is reserved for
  the finalize sum invariant) rather than a DB-`500`; the `CHECK` constraint remains a backstop.
- **INV-12 sum check at finalize.** `FinalizeRelease` (`draft → in_review`) validates
  `Σ(collaborator percent) ≤ 100` **per track** → else `422 SPLIT_OVER_100` (the error code already
  exists in the catalog error set). Submit does **not** block on `pending` — pending gates *go-live*,
  not submit.
- **Go-live guard — zero new code.** The WU-CAT-4 `CatalogRepository.hasPendingSplits(releaseId)`
  guard (already implemented + tested) now sees real `pending` rows and correctly blocks
  `in_review → live` until #2 confirms them. WU-CAT-6 adds no FSM/go-live logic.
- **INV-10 audit.** The split write occurs inside the existing privileged-mutation audit entry that
  `PATCH` already appends — no separate audit path, no double-audit.
- **Hexagonal.** All changes are catalog-internal (`adapter → application → domain`); no cross-module
  imports, no other module's tables. Payout subdivision (payments) *reads* `split_entry` in its own
  WU — out of scope here.

## Error handling

| Condition | Result |
|---|---|
| `percent` outside 0–100 on any `SplitRef` | `422` standard field-validation error (not `SPLIT_OVER_100`) |
| Finalize with `Σ percent > 100` on any track | `422 SPLIT_OVER_100` (INV-12) |
| Splits on a non-draft release (PATCH) | `409 ILLEGAL_TRANSITION` (existing draft-only guard) |
| `splits` on a `trackId` not on the release | not representable — splits are nested under the release's own tracks (decision 3) |

## Testing

- **Unit** — `UpdateReleaseService`: non-null `splits` replaces a track's set and stamps `pending`;
  null `splits` leaves existing rows untouched; empty `[]` clears them. `FinalizeRelease`: Σ=100 ✓,
  Σ=101 → `SPLIT_OVER_100`; multi-track validates per-track independently. Per-row percent bounds.
- **Integration (Testcontainers)** — `PATCH` with nested splits writes `split_entry` rows;
  `GET /:id` returns them via `TrackDraftView.splits`; finalize Σ>100 → 422; a persisted `pending`
  collaborator makes `hasPendingSplits` true and blocks a go-live attempt.
- **Contract** — `TrackDraftView.splits` conforms to the frontend `SplitEntry` shape (id, name,
  email, role, percent, confirmation), minus the stripped `self` entry; non-split releases still
  serialize with an empty `splits: []`.
- **ArchUnit** — unchanged rules stay green (no new cross-module edges).

## Files touched (anticipated)

- `catalog/application/port/in/UpdateRelease.java` — `TrackRef.splits` + `SplitRef`.
- `catalog/application/port/in/TrackDraftView.java` — `splits` + `SplitView`.
- `catalog/application/service/UpdateReleaseService.java` — apply/replace splits per track.
- `catalog/application/service/FinalizeReleaseService.java` — per-track Σ≤100 validation.
- `catalog/domain/Release.java` (+ `SplitEntry`) — split apply/replace on the draft aggregate.
- `catalog/application/port/out/CatalogRepository.java` + JPA adapter/entity/mapper — `split_entry`
  replace-set write + read into the view.
- `catalog/adapter/in/rest/StudioReleaseResource.java` (+ request/response DTOs) — nested `splits`
  in/out.
- `backend/docs/architecture/catalog.md` — flip the "deferred to WU-CAT-6" notes to as-built; update
  the split-persistence "Known gap" §.
- `API-CONTRACT.md` — document `splits` on the track draft shape.
- `backend/.project/backlog.yaml` — register **WU-CAT-6** (`depends_on: [WU-CAT-5]`) before starting.

## Non-goals (deferred — see Program context)

Invite emails/notifications; the `split_invite` token model + tokenized accept/decline endpoint +
resend + expiry sweep; a `declined` confirmation state; auto-confirm of registered collaborators; the
frontend Splits-step wiring; the collaborator-accept page. Independent of the human-gated **OQ-4**
(royalty model) — splits only define how the creator pool subdivides, whatever that pool is.
