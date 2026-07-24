# Frontend Admin Users Wiring — Design

**Date:** 2026-07-24
**Slice:** Admin console — slice 2 of ~5 remaining (Users). Reuses the `queries/admin-*` +
shared-mapper pattern established by the support slice (#163). First admin slice with a **detail
route** (`$userId`) and the first to adopt the **distinct error state** standard.
**Branch:** `feat/frontend-admin-users` off `master`
**Backend:** `AdminUsersResource` (`admin` module, WU-ADM-2/3) — already merged. No backend change.

## Goal

Wire the admin **Users** screens — the list (`admin.users`) and the detail (`admin.users.$userId`) —
from the mock `getAdminUsers()` / `getUserDetail()` to the live `AdminUsersResource` endpoints, with
**no visual change**. Establishes the detail-route ($param) wiring pattern the later slices (catalog
item, dispute) reuse, and introduces a distinct load-error affordance as the new admin standard.

## Context

- `Frontend/src/routes/admin.users.tsx` — a filterable user table. Seeds
  `useState(getAdminUsers())` + `USER_COUNTS` (filter-pill counts) and mutates the local array:
  `verify` sets `verified: true`; the row menu's Suspend/Reactivate flip `status`; a bulk **Suspend**
  acts on the multi-select set; a CSV-export button is a bare `toast`. Filters (all/fans/artists/
  verified/suspended) + free-text search run client-side.
- `Frontend/src/routes/admin.users.$userId.tsx` — a user profile: header (name/role/status/verify),
  action buttons (Verify / Suspend-with-reason-modal / Reactivate), an overflow menu (Log in as user,
  Reset password, Email user, Export data), an **action log**, and **Activity / Orders / Devices**
  sections. Seeds `getAdminUsers().find(id)` + `getUserDetail()` and mutates locally.
- `Frontend/src/lib/admin-data.ts` — the mock. Types (unchanged, reused):
  - `UserStatus = 'active' | 'pending' | 'suspended'`; `UserRole` (`fan` | `artist`).
  - `AdminUserRow { id, name, initial, email, role, verified, joined, lastActive, status }`
  - `USER_COUNTS { all, fans, artists, verified, suspended }`
  - `UserActionLog { id, action, by, time }`
  - `UserDetail { activity: UserActivity[], orders: UserOrder[], devices: UserDevice[] }`

### Backend surface (`AdminUsersResource`, base `/v1/admin/users`)

| Method | Path | Body | Returns | Roles |
|---|---|---|---|---|
| GET | `/` | `?page&size&filter&q` (defaults page 1 / size 100) | `PagedUsersDto` | all 5 admin |
| GET | `/{id}` | — | `UserDetailDto` | all 5 admin |
| POST | `/{id}/verify` | — | `AdminUserRowDto` | super-admin, moderator |
| POST | `/{id}/suspend` | `{ reason }` (non-blank) | `AdminUserRowDto` | super-admin, moderator |
| POST | `/{id}/reactivate` | — | `AdminUserRowDto` | super-admin, moderator |
| POST | `/{id}/impersonate` | — | `ImpersonationTokenDto` | super-admin |
| POST | `/{id}/data-export` | — | `Response` | super-admin, support |

- `PagedUsersDto = { items: AdminUserRowDto[], page, size, total, counts: { all, fans, artists,
  verified, suspended } }`.
- `AdminUserRowDto = { id, name, initial, email, role, verified, joined, lastActive, status }` — a
  **field-for-field match** for the mock `AdminUserRow`. No timestamp conversion needed (`joined` /
  `lastActive` are already display strings on the wire).
- `UserDetailDto = { summary: AdminUserRowDto, activity: Object[], orders: Object[], devices:
  Object[], actionLog: [{ id, action, by, time }] }`. **`activity` / `orders` / `devices` are served
  empty** (not yet modeled) — this slice does NOT consume them (see Out of scope). `actionLog`
  entries match the mock `UserActionLog` field-for-field.
- `SuspendRequest = { reason }` (`@NotBlank`).

`apiFetch` (`lib/api/client.ts`) already prepends `/v1` and handles the error envelope — no change.

## Architecture

- **New `Frontend/src/lib/api/queries/admin-users.ts`** (second `queries/admin-*` module):
  - `usersQuery()` → `GET /admin/users` → `PagedUsersDto` mapped to `{ users: AdminUserRow[], counts:
    UserCounts }`. Uses page 1 / size 100 (matches the mock's single-fetch + client-side filtering).
  - `userDetailQuery(id)` → `GET /admin/users/{id}` → `{ summary: AdminUserRow, actionLog:
    UserActionLog[] }` (activity/orders/devices ignored).
  - `apiVerifyUser(id)` → `POST /admin/users/{id}/verify`.
  - `apiSuspendUser(id, reason)` → `POST /admin/users/{id}/suspend` `{ reason }`.
  - `apiReactivateUser(id)` → `POST /admin/users/{id}/reactivate`.
  - Each mutation invalidates `usersQuery().queryKey` (`['admin','users','list']`); the detail-page
    mutations additionally invalidate `userDetailQuery(id).queryKey` (`['admin','users','detail',id]`).
- **Mappers** (`lib/api/mappers.ts`): `toAdminUserRow(wire)` (identity-shaped; `role`/`status` string →
  the unions), `toUserCounts(wire)`, `toUserActionLog(wire)`, and `toUserDetail(wire)` (= `{ summary:
  toAdminUserRow(wire.summary), actionLog: wire.actionLog.map(toUserActionLog) }`). Plus `*Wire` types.
  Imports go in the top import block (not mid-file — the recurring nit from the support slice).
- **`admin.users.tsx`**: replace `useState(getAdminUsers())` + `USER_COUNTS` with
  `useQuery(usersQuery())`; default `{ users: [], counts: all-zero }` while loading. Keep
  `filter`/`query`/`selected` as local UI state and the client-side filtering. The row actions become
  async handlers → mutation → invalidate → toast on success/failure. Quick-suspend (list + bulk) sends
  a default reason `'Suspended from user list'` (the list has no reason modal; `@NotBlank` only needs
  non-empty). All JSX/classes preserved.
- **`admin.users.$userId.tsx`**: replace the local `found`/`log` seeds with
  `useQuery(userDetailQuery(id))` for the header + action log; keep `getUserDetail()` for
  Activity/Orders/Devices. Verify/Reactivate → mutations; Suspend keeps the existing `SuspendModal` and
  passes its `reason` to `apiSuspendUser`. The overflow-menu items (Log in as user, Reset password,
  Email user, Export data) and the device Sign-out buttons stay as toasts (Category B). All JSX
  preserved.
- **Distinct error state (new admin standard):** the query modules already surface `isError` via
  TanStack. On the list, when `usersQuery` errors, render a small inline affordance in place of the
  empty-table body — `"Couldn't load users"` + a **Retry** button calling `refetch()` — visually
  distinct from the genuine empty result. Same treatment on the detail page (error → "Couldn't load
  this user" + Retry, instead of the not-found fallback). Reuse existing text/utility classes; no new
  design language. This is the pattern every remaining admin slice adopts.

## Out of scope

- **Activity / Orders / Devices** on the detail page — backend serves them empty; kept on the mock
  (`getUserDetail()`) so the page still looks complete. Wire once the backend populates them. (ADR-worthy
  note in the module ADD; recorded as a deliberate mock/live split.)
- **Impersonate, data-export, reset-password, email-user, device sign-out, CSV export** — kept as
  toasts. Impersonation needs a session-swap flow the SPA lacks; export triggers a file download;
  reset/email have no backend endpoint. Each is a follow-up, not this slice.
- Server-side `?filter&q` / pagination — the UI filters the full page client-side today; kept as-is
  (backend size=100 default). `total` (~48k) is not surfaced beyond the existing counts.
- No change to the other 14 admin routes.

## Testing & gate

- Co-located Vitest `admin-users.test.ts`: query URLs + mapping (list → users+counts; detail →
  summary+actionLog); verify/suspend/reactivate URLs + bodies (suspend carries `{ reason }`). Mapper
  cases in `mappers.test.ts` (row identity mapping, counts, action log, detail projection). RTL uses
  `toBeTruthy()` (no jest-dom).
- No visual change: JSX/classes preserved; only the data source, the wired actions, and the added
  error affordance change. Mapper outputs match `admin-data.ts` types exactly.
- Gate (from `Frontend/`, Node 22 via nvm): `npm run build` (`tsc -b`) + `npx vitest run` green; no
  NEW lint errors. Live QA signed in as an admin: view the list (counts + rows + filters), verify /
  suspend (default reason) / reactivate a user (row updates, persists), open a detail page (header +
  action log live; activity/orders/devices still shown), suspend-with-reason via the modal, and force
  a load error to see the Retry affordance. One PR: `feat/frontend-admin-users`.
