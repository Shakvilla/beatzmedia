# Frontend Admin Support Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the admin support inbox (`admin.support`) from the mock `getSupportTickets()` to the live `AdminSupportResource` endpoints, no visual change. First admin-console slice.

**Architecture:** TanStack `queryOptions` + `apiFetch<Wire>` + `mappers.toX` for the read; mutations = async `apiFetch` fns + `queryClient.invalidateQueries` on success. New `lib/api/queries/admin-support.ts` (the first `queries/admin-*` module). One new `relativeTime` helper in `lib/format.ts` converts the wire's ISO timestamps to the mock's relative strings.

**Tech Stack:** React 19 + TanStack Query v5 + TanStack Router, TypeScript, Vitest + RTL, Node 22 via nvm.

**Spec:** `docs/superpowers/specs/2026-07-22-frontend-admin-support-wiring-design.md`

## Global Constraints

- **No visual change.** Data-source swap only; JSX/classes preserved.
- Mapper outputs match `Frontend/src/lib/admin-data.ts` types exactly: `SupportTicket { id, subject, requester, channel, priority: TicketPriority, status: TicketStatus, age, messages: SupportMessage[] }`; `SupportMessage { id, from: 'user'|'agent', author, text, time }`; `TicketStatus = 'open'|'pending'|'resolved'`; `TicketPriority = 'high'|'normal'|'low'`.
- The wire's `SupportTicketDto.age` and `SupportMessageDto.time` are **ISO-8601 timestamps**; the UI shows relative strings. The mapper converts them: ticket `age` → `"2h"` (via `relativeTime`), message `time` → `"2h ago"` / `"just now"` (via `relativeTimeAgo`).
- `apiFetch` (`lib/api/client.ts`) prepends `/v1`; paths written WITHOUT `/v1`. Handles the JSON error envelope. No client change.
- Backend (all merged, `@RolesAllowed` the five admin roles): `GET /admin/support/tickets` → `SupportTicketDto[]` (full threads inline); `POST /admin/support/tickets/{id}/reply` `{text}` → 201; `POST .../{id}/assign` `{assigneeId}` → ticket; `POST .../{id}/resolve` → ticket.
- **Assign is assign-to-self**: send the current admin's `account.id` (from `useAuth()`).
- No `@testing-library/jest-dom` → RTL uses `toBeTruthy()`.
- All commands from `Frontend/`. Real typecheck gate: `npm run build` (`tsc -b`), NOT `tsc --noEmit`. Test: `npx vitest run <path>`. Lint: `npm run lint` — add no NEW errors.
- Branch `feat/frontend-admin-support` (already created; spec committed). NEVER stage `backend/src/main/resources/application.properties` or `backend/docker-compose.yml`.

---

## File Structure

- `Frontend/src/lib/format.ts` (modify) — add `relativeTime`, `relativeTimeAgo`.
- `Frontend/src/lib/format.test.ts` (create or modify) — helper cases.
- `Frontend/src/lib/api/mappers.ts` (modify) — `SupportTicketWire`/`SupportMessageWire`, `toSupportTicket`, `toSupportMessage`.
- `Frontend/src/lib/api/mappers.test.ts` (modify) — mapper cases.
- `Frontend/src/lib/api/queries/admin-support.ts` (new) — `supportTicketsQuery`, `apiReplyToTicket`, `apiAssignTicket`, `apiResolveTicket`.
- `Frontend/src/lib/api/queries/admin-support.test.ts` (new) — URLs, bodies.
- `Frontend/src/routes/admin.support.tsx` (modify) — query + 3 mutations + useAuth.

**Task order:** format helper → mappers → query layer → wire route. Each task compiles green.

---

## Task 1: `relativeTime` / `relativeTimeAgo` in `format.ts`

**Files:**
- Modify: `Frontend/src/lib/format.ts`
- Test: `Frontend/src/lib/format.test.ts`

**Interfaces:**
- Produces: `relativeTime(iso: string, now?: number): string`, `relativeTimeAgo(iso: string, now?: number): string`.

**Prep:** Open `format.ts` to match the existing function/export style (see `formatCount`). The `now` param (defaulting to `Date.now()`) makes the helpers deterministically testable.

- [ ] **Step 1: Write failing tests** in `format.test.ts` (create if absent; if present, append):

```ts
import { describe, it, expect } from 'vitest'
import { relativeTime, relativeTimeAgo } from './format'

const NOW = Date.parse('2026-07-22T12:00:00Z')

describe('relativeTime', () => {
  it('just now under a minute', () => {
    expect(relativeTime('2026-07-22T11:59:30Z', NOW)).toBe('just now')
  })
  it('minutes', () => { expect(relativeTime('2026-07-22T11:30:00Z', NOW)).toBe('30m') })
  it('hours', () => { expect(relativeTime('2026-07-22T10:00:00Z', NOW)).toBe('2h') })
  it('days', () => { expect(relativeTime('2026-07-19T12:00:00Z', NOW)).toBe('3d') })
})

describe('relativeTimeAgo', () => {
  it('appends ago for non-zero', () => { expect(relativeTimeAgo('2026-07-22T10:00:00Z', NOW)).toBe('2h ago') })
  it('keeps just now as-is', () => { expect(relativeTimeAgo('2026-07-22T11:59:40Z', NOW)).toBe('just now') })
})
```

- [ ] **Step 2: Run to verify fail:** `npx vitest run src/lib/format.test.ts` → FAIL.

- [ ] **Step 3: Implement** in `format.ts`:

```ts
/** ISO-8601 → compact relative age: "just now" | "5m" | "2h" | "3d". */
export function relativeTime(iso: string, now: number = Date.now()): string {
  const diffMs = now - Date.parse(iso)
  const sec = Math.max(0, Math.floor(diffMs / 1000))
  if (sec < 60) return 'just now'
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min}m`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr}h`
  return `${Math.floor(hr / 24)}d`
}

/** Like relativeTime but suffixed " ago" (except "just now"): "2h ago" | "just now". */
export function relativeTimeAgo(iso: string, now: number = Date.now()): string {
  const r = relativeTime(iso, now)
  return r === 'just now' ? r : `${r} ago`
}
```

- [ ] **Step 4: Run to verify pass:** `npx vitest run src/lib/format.test.ts` → PASS. `npm run build` → 0 errors.

- [ ] **Step 5: Commit** — `feat(admin): relativeTime/relativeTimeAgo format helpers`.

---

## Task 2: Support mappers

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Consumes: `SupportTicket`, `SupportMessage`, `TicketStatus`, `TicketPriority` from `../../admin-data`; `relativeTime`, `relativeTimeAgo` from `../../format`.
- Produces: `SupportTicketWire`, `SupportMessageWire`, `toSupportTicket`, `toSupportMessage`.

**Prep:** Confirm the `admin-data.ts` types and match the existing mapper style in `mappers.ts` (see `toStudioEpisode`). Note the import paths from `src/lib/api/mappers.ts`: `../../admin-data` and `../../format`.

- [ ] **Step 1: Write failing tests** in `mappers.test.ts`:

```ts
import { toSupportTicket, toSupportMessage } from './mappers'

const NOW = Date.parse('2026-07-22T12:00:00Z')

describe('toSupportMessage', () => {
  it('maps fields + relative time', () => {
    expect(toSupportMessage({ id: 'm1', from: 'agent', author: 'Yaa', text: 'hi',
      time: '2026-07-22T10:00:00Z' }, NOW)).toEqual({ id: 'm1', from: 'agent', author: 'Yaa', text: 'hi', time: '2h ago' })
  })
})

describe('toSupportTicket', () => {
  it('maps fields, relative age, nested messages', () => {
    const t = toSupportTicket({ id: 't1', subject: 'Payout', requester: 'Black Sherif', channel: 'email',
      priority: 'high', status: 'open', age: '2026-07-22T10:00:00Z',
      messages: [{ id: 'm1', from: 'user', author: 'BS', text: 'q', time: '2026-07-22T11:59:40Z' }] }, NOW)
    expect(t).toEqual({ id: 't1', subject: 'Payout', requester: 'Black Sherif', channel: 'email',
      priority: 'high', status: 'open', age: '2h',
      messages: [{ id: 'm1', from: 'user', author: 'BS', text: 'q', time: 'just now' }] })
  })
})
```

- [ ] **Step 2: Run to verify fail:** `npx vitest run src/lib/api/mappers.test.ts` → FAIL.

- [ ] **Step 3: Add mappers** to `mappers.ts` (the `now` param defaults so production calls need no argument; tests pass a fixed `now`):

```ts
import type { SupportTicket, SupportMessage, TicketStatus, TicketPriority } from '../../admin-data'
import { relativeTime, relativeTimeAgo } from '../../format'

export interface SupportMessageWire { id: string; from: string; author: string; text: string; time: string }
export function toSupportMessage(w: SupportMessageWire, now?: number): SupportMessage {
  return { id: w.id, from: w.from as 'user' | 'agent', author: w.author, text: w.text, time: relativeTimeAgo(w.time, now) }
}

export interface SupportTicketWire {
  id: string; subject: string; requester: string; channel: string
  priority: string; status: string; age: string; messages: SupportMessageWire[]
}
export function toSupportTicket(w: SupportTicketWire, now?: number): SupportTicket {
  return {
    id: w.id, subject: w.subject, requester: w.requester, channel: w.channel,
    priority: w.priority as TicketPriority, status: w.status as TicketStatus,
    age: relativeTime(w.age, now), messages: w.messages.map((m) => toSupportMessage(m, now)),
  }
}
```

- [ ] **Step 4: Run to verify pass:** `npx vitest run src/lib/api/mappers.test.ts` → PASS. `npm run build` → 0 errors.

- [ ] **Step 5: Commit** — `feat(admin): support ticket/message wire mappers`.

---

## Task 3: `admin-support.ts` query + mutation layer

**Files:**
- Create: `Frontend/src/lib/api/queries/admin-support.ts`
- Test: `Frontend/src/lib/api/queries/admin-support.test.ts`

**Interfaces:**
- Consumes: `apiFetch`; `toSupportTicket`/`SupportTicketWire` (Task 2); `SupportTicket` type.
- Produces:
  - `supportTicketsQuery()` → `queryOptions` for `SupportTicket[]`
  - `apiReplyToTicket(id: string, text: string): Promise<void>`
  - `apiAssignTicket(id: string, assigneeId: string): Promise<void>`
  - `apiResolveTicket(id: string): Promise<void>`

**Prep:** Read `lib/api/queries/studio.ts` for the `queryOptions` + `apiFetch` idiom and the void-POST/PATCH shape. The mutations return `Promise<void>` — `apiFetch` parses and discards any body (reply's 201 message / assign+resolve's ticket are not consumed; the caller invalidates instead).

- [ ] **Step 1: Write failing tests** `admin-support.test.ts` (mock `../client`):

```ts
import { vi, describe, it, expect, beforeEach } from 'vitest'
import * as client from '../client'
import { supportTicketsQuery, apiReplyToTicket, apiAssignTicket, apiResolveTicket } from './admin-support'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))
const apiFetch = vi.mocked(client.apiFetch)
const TICKET = { id: 't1', subject: 'S', requester: 'R', channel: 'email', priority: 'high',
  status: 'open', age: '2026-07-22T10:00:00Z', messages: [] }

beforeEach(() => apiFetch.mockReset())

describe('supportTicketsQuery', () => {
  it('GETs /admin/support/tickets and maps', async () => {
    apiFetch.mockResolvedValue([TICKET])
    const res = await supportTicketsQuery().queryFn!({} as never)
    expect(apiFetch).toHaveBeenCalledWith('/admin/support/tickets')
    expect(res[0].id).toBe('t1')
    expect(res[0].priority).toBe('high')
  })
})

describe('mutations', () => {
  it('reply POSTs text', async () => {
    apiFetch.mockResolvedValue({})
    await apiReplyToTicket('t1', 'hello')
    expect(apiFetch).toHaveBeenCalledWith('/admin/support/tickets/t1/reply', { method: 'POST', body: { text: 'hello' } })
  })
  it('assign POSTs assigneeId', async () => {
    apiFetch.mockResolvedValue({})
    await apiAssignTicket('t1', 'acct-9')
    expect(apiFetch).toHaveBeenCalledWith('/admin/support/tickets/t1/assign', { method: 'POST', body: { assigneeId: 'acct-9' } })
  })
  it('resolve POSTs', async () => {
    apiFetch.mockResolvedValue({})
    await apiResolveTicket('t1')
    expect(apiFetch).toHaveBeenCalledWith('/admin/support/tickets/t1/resolve', { method: 'POST' })
  })
})
```

- [ ] **Step 2: Run to verify fail:** `npx vitest run src/lib/api/queries/admin-support.test.ts` → FAIL.

- [ ] **Step 3: Implement** `admin-support.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import type { SupportTicket } from '../../admin-data'
import { apiFetch } from '../client'
import { toSupportTicket, type SupportTicketWire } from '../mappers'

/** `GET /v1/admin/support/tickets` — the full inbox with threads inline (admin roles only). */
export function supportTicketsQuery() {
  return queryOptions({
    queryKey: ['admin', 'support', 'tickets'],
    queryFn: async () =>
      (await apiFetch<SupportTicketWire[]>('/admin/support/tickets')).map((t) => toSupportTicket(t)),
  })
}

/** `POST /v1/admin/support/tickets/:id/reply` — append an agent reply. */
export function apiReplyToTicket(id: string, text: string): Promise<void> {
  return apiFetch<void>(`/admin/support/tickets/${id}/reply`, { method: 'POST', body: { text } })
}

/** `POST /v1/admin/support/tickets/:id/assign` — assign the ticket (assign-to-self: pass own account id). */
export function apiAssignTicket(id: string, assigneeId: string): Promise<void> {
  return apiFetch<void>(`/admin/support/tickets/${id}/assign`, { method: 'POST', body: { assigneeId } })
}

/** `POST /v1/admin/support/tickets/:id/resolve` — mark resolved. */
export function apiResolveTicket(id: string): Promise<void> {
  return apiFetch<void>(`/admin/support/tickets/${id}/resolve`, { method: 'POST' })
}
```

Note `toSupportTicket(t)` is called with no `now` arg → uses `Date.now()` at render, correct for production.

- [ ] **Step 4: Run to verify pass:** `npx vitest run src/lib/api/queries/admin-support.test.ts` → PASS. `npm run build` → 0 errors.

- [ ] **Step 5: Commit** — `feat(admin): support tickets query + mutation layer`.

---

## Task 4: Wire `admin.support.tsx`

**Files:**
- Modify: `Frontend/src/routes/admin.support.tsx`

**Interfaces:**
- Consumes: `supportTicketsQuery`, `apiReplyToTicket`, `apiAssignTicket`, `apiResolveTicket` (Task 3); `useQuery`, `useQueryClient`; `useAuth` from `../features/auth/auth-context`.

**Prep:** Read the whole current file. Preserve every JSX element and class. Keep the `SupportTicket`/`SupportMessage`/`TicketStatus`/`TicketPriority` **type** imports from `../lib/admin-data` (used by `PriorityDot`/`StatusPill`/local typings); only the `getSupportTickets` DATA getter import goes away. Keep `filter`/`query`/`activeId`/`reply` local UI state and the `list`/`active` derivations.

- [ ] **Step 1: Swap the data source.** Replace the `getSupportTickets` import + `useState` seeds:

```ts
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { supportTicketsQuery, apiReplyToTicket, apiAssignTicket, apiResolveTicket } from '../lib/api/queries/admin-support'
import { useAuth } from '../features/auth/auth-context'
// keep: import { type SupportTicket, type SupportMessage, type TicketStatus, type TicketPriority } from '../lib/admin-data'
```

In `AdminSupport()`:

```ts
const queryClient = useQueryClient()
const { account } = useAuth()
const { data: tickets = [] } = useQuery(supportTicketsQuery())
// keep: filter, query, activeId, reply as useState; keep the `list` useMemo and `active` derivation.
const invalidate = () => queryClient.invalidateQueries({ queryKey: supportTicketsQuery().queryKey })
```

Remove the `const [tickets, setTickets] = useState(...)` seed and the `activeId` seed's `getSupportTickets()[0]` (initialize `activeId` to `''`; `active` already falls back to `list[0]`).

- [ ] **Step 2: Rewire the three actions** as async + invalidate (drop local `setTickets`):

```ts
const send = async () => {
  if (!reply.trim() || !active) return
  try {
    await apiReplyToTicket(active.id, reply.trim())
    setReply('')
    await invalidate()
    toast('Reply sent', 'success')
  } catch (e) { toast(e instanceof Error ? e.message : 'Could not send reply', 'error') }
}
const assign = async () => {
  if (!active || !account) return
  try { await apiAssignTicket(active.id, account.id); await invalidate(); toast('Assigned to you', 'success') }
  catch (e) { toast(e instanceof Error ? e.message : 'Could not assign', 'error') }
}
const resolve = async () => {
  if (!active) return
  try { await apiResolveTicket(active.id); await invalidate(); toast('Ticket resolved', 'success') }
  catch (e) { toast(e instanceof Error ? e.message : 'Could not resolve', 'error') }
}
```

Wire the buttons: Assign `onClick={assign}` (was the bare toast); Resolve `onClick={resolve}` (was `setStatus('resolved', ...)`); the send button + Cmd/Ctrl-Enter already call `send`. Delete the old `setStatus` helper. The reply textarea keeps its `onKeyDown` Cmd/Ctrl+Enter → `send`.

- [ ] **Step 3: Typecheck + tests + lint.** `npm run build` → 0 errors; `npx vitest run` whole suite green; `npm run lint` no NEW errors vs baseline.

- [ ] **Step 4: Commit** — `feat(admin): wire support inbox to live tickets (reply/assign/resolve)`.

---

## Task 5: Live QA + verification gate + PR

**Files:** none (verification only).

- [ ] **Step 1: Full frontend gate** (from `Frontend/`, Node 22 via nvm): `npm run build` clean; `npx vitest run` all green; `npm run lint` no NEW errors.

- [ ] **Step 2: Live QA** against the running stack (beatzmedia backend up; signed in as an **admin** account — a fresh signup is a fan, so this needs an admin; note in the PR if no admin seed is available locally):
  - `/admin/support` renders the live inbox (tickets with relative ages), filters + search work.
  - Open a ticket → thread shows with relative message times.
  - Reply → message appears after refetch; status reflects the server transition.
  - Assign → toast; persists across refetch.
  - Resolve → status → resolved.
  - No visual difference from the mock version.

- [ ] **Step 3: Push + PR.** Push `feat/frontend-admin-support`; open a PR titled `feat(admin): wire support inbox to WU-ADM-7 (tickets/reply/assign/resolve)`, body summarizing the data-swap, the new `relativeTime` helper + `queries/admin-*` pattern, assign-to-self, no-backend-change, and test/QA evidence. NEVER stage `application.properties` / `docker-compose.yml`.

- [ ] **Step 4:** After CI green + review, squash-merge (strict branch protection — auto-merge only when all required checks pass).

---

## Self-Review notes

- **Spec coverage:** inbox read (T2/T3/T4), reply/assign/resolve mutations (T3/T4), ISO→relative age/time (T1/T2), assign-to-self via useAuth (T4). ✓
- **Type consistency:** `relativeTime`/`relativeTimeAgo` (T1) consumed by mappers (T2); `toSupportTicket`/`SupportTicketWire` (T2) consumed by `supportTicketsQuery` (T3); `supportTicketsQuery().queryKey` reused for invalidation in T4. ✓
- **No placeholders:** every code step carries complete code. ✓
- **Admin-console pattern:** `queries/admin-support.ts` + the `['admin', ...]` query-key convention established here are the template for the remaining admin slices.
