# Frontend Admin Support Wiring — Design

**Date:** 2026-07-22
**Slice:** Admin console — slice 1 of ~6 (support inbox). Establishes the `queries/admin-*` pattern.
**Branch:** `feat/frontend-admin-support` off `master`
**Backend:** WU-ADM-7 (`admin` module support tickets) — already merged. No backend change.

## Goal

Wire the admin **support inbox** (`admin.support`) from the mock `getSupportTickets()` to the live
`AdminSupportResource` endpoints, with **no visual change**. First admin-console slice; the query
module + mapper approach it establishes is reused by the later admin slices.

## Context

- `Frontend/src/routes/admin.support.tsx` — a two-pane inbox: left = ticket list (status filter
  open/pending/resolved/all + free-text search), right = conversation (message thread + reply box +
  **Assign** and **Resolve** buttons). Today it seeds `useState(getSupportTickets())` and mutates
  that local array: `send` appends an agent message + flips status to `pending`; the Resolve button
  sets status `resolved`; the **Assign** button is a bare `toast('Assigned to you')` (no backend).
- `Frontend/src/lib/admin-data.ts` — the mock. Types (unchanged, reused):
  - `TicketStatus = 'open' | 'pending' | 'resolved'`, `TicketPriority = 'high' | 'normal' | 'low'`
  - `SupportMessage { id, from: 'user' | 'agent', author, text, time }`
  - `SupportTicket { id, subject, requester, channel, priority, status, age, messages: SupportMessage[] }`

### Backend surface (WU-ADM-7, `AdminSupportResource`, base `/v1/admin/support/tickets`, `@RolesAllowed({super-admin, finance, moderator, editor, support})`)

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/` | `?status=&q=&page=&size=` (defaults page 1 / size 100) | `SupportTicketDto[]` |
| GET | `/{id}` | — | `SupportTicketDto` |
| POST | `/{id}/reply` | `{ text }` (non-blank) | **201** `SupportMessageDto` |
| POST | `/{id}/assign` | `{ assigneeId }` (non-blank) | `SupportTicketDto` |
| POST | `/{id}/resolve` | — | `SupportTicketDto` |

`SupportTicketDto` = `{ id, subject, requester, channel, priority, status, age, messages:
[{ id, from, author, text, time }] }` — a **1:1 match** for the frontend `SupportTicket`, and the
**list already returns full messages inline** (`SupportTicketDto.from(SupportTicketDetailView)`), so a
single query serves both the inbox list and the active thread, exactly like the mock's one
`getSupportTickets()`.

**The one real mapping detail:** on the wire, `SupportTicketDto.age` is `createdAt().toString()` and
`SupportMessageDto.time` is `time().toString()` — both **ISO-8601 timestamps**, whereas the UI shows
relative strings ("2h", "1h ago", "just now"). There is **no relative-time helper** in the frontend
yet. The mapper must convert ISO → relative to preserve the look.

`apiFetch` (`lib/api/client.ts`) already prepends `/v1`, handles the JSON error envelope, and needs
no changes. This is the same idiom as the merged studio slices.

## Architecture

- **New `Frontend/src/lib/api/queries/admin-support.ts`** (the first `queries/admin-*` module):
  - `supportTicketsQuery()` → `GET /admin/support/tickets` → `toSupportTicket[]`.
  - `apiReplyToTicket(id, text)` → `POST /admin/support/tickets/{id}/reply` `{ text }`.
  - `apiAssignTicket(id, assigneeId)` → `POST /admin/support/tickets/{id}/assign` `{ assigneeId }`.
  - `apiResolveTicket(id)` → `POST /admin/support/tickets/{id}/resolve`.
  - Each mutation invalidates `supportTicketsQuery().queryKey` on success (the list carries the full
    threads + statuses, so one invalidation refreshes both panes).
- **`lib/format.ts`** — a new small `relativeTime(iso: string): string` helper:
  `< 60s → "just now"`, `< 60m → "Nm"`, `< 24h → "Nh"`, else `"Nd"`. Plus a thin
  `relativeTimeAgo(iso)` = `relativeTime` with `" ago"` appended except for "just now" — so ticket
  `age` renders `"2h"` and message `time` renders `"2h ago" / "just now"`, matching the mock's two
  wordings.
- **Mappers** (`lib/api/mappers.ts`): `toSupportMessage` (`time` = `relativeTimeAgo(wire.time)`;
  `from` string → `'user' | 'agent'`) and `toSupportTicket` (`age` = `relativeTime(wire.age)`;
  `priority`/`status` string → the unions; `messages` via `toSupportMessage`). Plus `*Wire` types.
- **`admin.support.tsx`**: replace `useState(getSupportTickets())` with
  `useQuery(supportTicketsQuery())` (default `[]` while loading). The three actions become async
  handlers that call the mutation, `await` an invalidation, and toast on success/failure (dropping
  the local optimistic `setTickets`). Keep `filter` / `query` / `activeId` as local UI state and the
  client-side `list` filtering (matches the mock; the backend `?status&q` params stay unused). All
  JSX/classes preserved.
- **Assign target:** the button's label + toast (`Assigned to you`) mean *assign-to-me*, so send the
  **current admin's own account id** — add `useAuth()` and pass `account.id` as `assigneeId`. No new
  UI; the button behavior is unchanged from the user's view, only now it persists.

## Out of scope

- Server-side `?status=&q=` filtering / pagination — the UI filters the full list client-side today;
  kept as-is (backend defaults size=100, enough for the inbox).
- Assigning to *another* agent (the UI has no assignee picker) — assign-to-self only.
- No change to the other 17 admin routes; this slice touches only support + the shared mapper/format
  files.

## Testing & gate

- Co-located Vitest: `admin-support.test.ts` (query URL + mapping; reply/assign/resolve URLs + bodies)
  and `format` cases for `relativeTime`/`relativeTimeAgo` (boundaries: just-now / minutes / hours /
  days) + mapper cases in `mappers.test.ts`. RTL uses `toBeTruthy()` (no jest-dom).
- No visual change: JSX/classes preserved; only data source + the three actions swapped.
- Mapper outputs match `admin-data.ts` types exactly.
- Gate (from `Frontend/`, Node 22 via nvm): `npm run build` (`tsc -b`) + `npx vitest run` green; no
  NEW lint errors. Live QA against the running stack, signed in as an admin: view the inbox, open a
  ticket, reply (see the message appear + status→pending), assign (persists), resolve
  (status→resolved). One PR: `feat/frontend-admin-support`.
